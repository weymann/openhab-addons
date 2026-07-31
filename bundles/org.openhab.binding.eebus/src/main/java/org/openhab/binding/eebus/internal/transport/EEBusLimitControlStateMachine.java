/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.eebus.internal.transport;

import static org.openhab.binding.eebus.internal.transport.EEBusLimitControlState.FAILSAFE;
import static org.openhab.binding.eebus.internal.transport.EEBusLimitControlState.INIT;
import static org.openhab.binding.eebus.internal.transport.EEBusLimitControlState.LIMITED;
import static org.openhab.binding.eebus.internal.transport.EEBusLimitControlState.UNLIMITED_AUTONOMOUS;
import static org.openhab.binding.eebus.internal.transport.EEBusLimitControlState.UNLIMITED_CONTROLLED;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable, protocol-agnostic implementation of the LPC/LPP "Controllable System" state machine
 * (transitions §2.3.3, states §2.3.2 of {@code EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_
 * public.pdf} - LPP is confirmed structurally identical in CONCEPT.md §5.4.2). Shared by
 * {@link EEBusLpcServerUseCase} and {@link EEBusLppServerUseCase} via
 * {@link AbstractEEBusLimitControllableSystemUseCase}.
 *
 * <p>
 * <strong>Deliberate simplification, disclosed:</strong> the primary spec defines transitions 1-3
 * and 8-12 in terms of "Heartbeat and a following write within 120 seconds" - i.e. two
 * conceptually separate 120s windows (one anchored to the heartbeat, one to whether a write
 * followed it). This class collapses that into a single "time since last Heartbeat" watchdog:
 * whenever that watchdog fires (no Heartbeat for {@value #HEARTBEAT_TIMEOUT_SECONDS}s), the
 * machine leaves any Energy-Guard-controlled state (transitions 3, 5, 7, 10's second branch) -
 * the safety-relevant property ("stop trusting a silent Energy Guard") is preserved exactly;
 * only the fine distinction of exactly which sub-condition within that window caused the
 * transition is not modeled bit-for-bit. Chosen deliberately over a more literal but more
 * fragile dual-timer reproduction - see CONCEPT.md §7 for the corresponding checklist entry.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusLimitControlStateMachine {

    /** Hardcoded per spec ("no Heartbeat received within 120 seconds") - not configurable. */
    static final long HEARTBEAT_TIMEOUT_SECONDS = 120;

    /** Called whenever the state changes, so the caller can update SPINE data and Items. */
    public interface Listener {
        void onStateChanged(EEBusLimitControlState newState);
    }

    private final Logger logger = LoggerFactory.getLogger(EEBusLimitControlStateMachine.class);
    private final ScheduledExecutorService scheduler;
    private final LongSupplier failsafeDurationMinimumSecondsSupplier;
    private final Listener listener;

    private final Object lock = new Object();
    private EEBusLimitControlState state = INIT;
    private @Nullable ScheduledFuture<?> heartbeatWatchdog;
    private @Nullable ScheduledFuture<?> failsafeDurationWatchdog;

    /**
     * @param scheduler used for the Heartbeat and failsafe-duration timers; the caller owns its
     *            lifecycle (this class never shuts it down, see {@link #close()})
     * @param failsafeDurationMinimumSecondsSupplier returns the currently configured
     *            {@code FailsafeDurationMinimum} value in seconds (may change at runtime if the
     *            Energy Guard writes a new value - read fresh each time it's needed, not cached)
     * @param listener notified on every state change
     */
    public EEBusLimitControlStateMachine(ScheduledExecutorService scheduler,
            LongSupplier failsafeDurationMinimumSecondsSupplier, Listener listener) {
        this.scheduler = scheduler;
        this.failsafeDurationMinimumSecondsSupplier = failsafeDurationMinimumSecondsSupplier;
        this.listener = listener;
        // Transition 3 (Init -> Unlimited/autonomous): if neither a Heartbeat nor a limit write
        // ever arrives, the CS must not stay in "init" (failsafe-limited) forever.
        rearmHeartbeatWatchdog();
    }

    public EEBusLimitControlState getState() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Call whenever a Heartbeat notification/read result is received from the Energy Guard
     * partner (see {@code AbstractEEBusLimitControllableSystemUseCase#onEnergyGuardHeartbeat}).
     */
    public void onHeartbeatReceived() {
        synchronized (lock) {
            rearmHeartbeatWatchdog();
        }
    }

    /**
     * Call whenever the Energy Guard writes to the Active Power Consumption/Production Limit
     * (scenario 1) - including the framework's own automatic deactivation when a limit's
     * duration expires (see {@code LimitListDataFunction}, which re-fires the same write
     * listener on expiry).
     *
     * @param active {@code true} if the written limit is activated with a usable value
     */
    public void onLimitWritten(boolean active) {
        synchronized (lock) {
            transitionTo(active ? LIMITED : UNLIMITED_CONTROLLED);
        }
    }

    private void rearmHeartbeatWatchdog() {
        ScheduledFuture<?> previous = heartbeatWatchdog;
        if (previous != null) {
            previous.cancel(false);
        }
        heartbeatWatchdog = scheduler.schedule(this::onHeartbeatTimeout, HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void onHeartbeatTimeout() {
        synchronized (lock) {
            switch (state) {
                case LIMITED:
                case UNLIMITED_CONTROLLED:
                    // Transitions 5, 7: Energy Guard went silent while we were relying on it.
                    transitionTo(FAILSAFE);
                    break;
                case INIT:
                case FAILSAFE:
                    // Transitions 3, 10 (second branch): never got a working connection, or the
                    // Energy Guard is still silent after already having left the CS in failsafe.
                    transitionTo(UNLIMITED_AUTONOMOUS);
                    break;
                case UNLIMITED_AUTONOMOUS:
                    // No further heartbeat-timeout transition is defined from this state - the CS
                    // simply stays uncontrolled until a fresh Heartbeat/write arrives.
                    break;
            }
        }
    }

    private void transitionTo(EEBusLimitControlState newState) {
        if (newState == state) {
            return;
        }
        logger.debug("EEBus limit control state {} -> {}", state, newState);
        state = newState;
        if (newState == FAILSAFE) {
            armFailsafeDurationWatchdog();
        } else {
            ScheduledFuture<?> previous = failsafeDurationWatchdog;
            if (previous != null) {
                previous.cancel(false);
                failsafeDurationWatchdog = null;
            }
        }
        listener.onStateChanged(newState);
    }

    private void armFailsafeDurationWatchdog() {
        long seconds = failsafeDurationMinimumSecondsSupplier.getAsLong();
        failsafeDurationWatchdog = scheduler.schedule(() -> {
            synchronized (lock) {
                // Transition 10 (first branch): minimum failsafe duration has elapsed.
                if (state == FAILSAFE) {
                    transitionTo(UNLIMITED_AUTONOMOUS);
                }
            }
        }, seconds, TimeUnit.SECONDS);
    }

    /** Cancels any pending timers. Does not shut down the shared {@link #scheduler}. */
    public void close() {
        synchronized (lock) {
            ScheduledFuture<?> hb = heartbeatWatchdog;
            if (hb != null) {
                hb.cancel(false);
            }
            ScheduledFuture<?> fd = failsafeDurationWatchdog;
            if (fd != null) {
                fd.cancel(false);
            }
        }
    }
}
