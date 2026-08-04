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

import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the pool of free WebSocket ports ({@value #PORT_RANGE_START}-{@value #PORT_RANGE_END})
 * that {@code eebus:service} Bridges (see {@code EEBusHandler}) bind their local SHIP server to.
 *
 * <p>
 * One OSGi-singleton instance is shared by every {@code EEBusHandler} in this binding, injected
 * via {@code EEBusHandlerFactory} (per java-coding-rules.md's dependency-injection rule), so port
 * assignment stays consistent across all {@code eebus:service} Bridges even though each one binds
 * its own, independent SHIP server.
 * </p>
 *
 * <p>
 * Lifecycle contract for callers, mirroring the {@code StorageService} pattern documented in
 * java-coding-rules.md: a port is taken out of the pool in {@code initialize()} - either the
 * Thing's explicitly configured port via {@link #reservePort(int)}, or, if none was configured,
 * an arbitrary free port via {@link #acquireFreePort()} - and returned to the pool in
 * {@code handleRemoval()}, not {@code dispose()}, via {@link #releasePort(int)}. {@code dispose()}
 * runs on every disable/update/restart cycle; releasing there would let a different Bridge grab
 * the still-in-use port out from under a Bridge that is merely restarting.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(service = EEBusPortPool.class)
public class EEBusPortPool {

    /** First port handed out by the pool (inclusive). */
    public static final int PORT_RANGE_START = 4711;

    /** Last port handed out by the pool (inclusive). */
    public static final int PORT_RANGE_END = 4810;

    private final Logger logger = LoggerFactory.getLogger(EEBusPortPool.class);

    private final NavigableSet<Integer> freePorts = new ConcurrentSkipListSet<>();

    public EEBusPortPool() {
        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            freePorts.add(port);
        }
    }

    /**
     * Takes the lowest-numbered free port out of the pool and returns it - used in
     * {@code initialize()} when the Thing has no explicitly configured port.
     *
     * @return the acquired port, now removed from the pool; empty if the pool
     *         ({@value #PORT_RANGE_START}-{@value #PORT_RANGE_END}) is exhausted
     */
    public Optional<Integer> acquireFreePort() {
        Integer port = freePorts.pollFirst();
        if (port == null) {
            logger.warn("EEBus port pool exhausted ({}-{}) - no free port left to acquire", PORT_RANGE_START,
                    PORT_RANGE_END);
            return Optional.empty();
        }
        return Optional.of(port);
    }

    /**
     * Removes a specific, explicitly configured port from the pool - used in {@code initialize()}
     * when the Thing has a configured port, so that {@link #acquireFreePort()} never hands the
     * same port out to another Bridge.
     *
     * @param port the configured port to reserve
     * @return {@code true} if the port was free and is now reserved; {@code false} if it was
     *         outside the pool's managed range ({@value #PORT_RANGE_START}-{@value #PORT_RANGE_END},
     *         e.g. a manually configured port outside that range) or already reserved by another
     *         Bridge
     */
    public boolean reservePort(int port) {
        boolean reserved = freePorts.remove(port);
        if (!reserved) {
            logger.debug("Port {} not reserved from the pool ({}-{}) - outside range or already in use", port,
                    PORT_RANGE_START, PORT_RANGE_END);
        }
        return reserved;
    }

    /**
     * Returns a previously acquired/reserved port to the pool - used in {@code handleRemoval()},
     * never {@code dispose()} (see class javadoc).
     *
     * @param port the port to release; silently ignored if outside the pool's managed range
     *            ({@value #PORT_RANGE_START}-{@value #PORT_RANGE_END}), since such a port was
     *            never taken out of this pool in the first place
     */
    public void releasePort(int port) {
        if (port < PORT_RANGE_START || port > PORT_RANGE_END) {
            logger.debug("Not releasing port {} - outside pool range ({}-{})", port, PORT_RANGE_START, PORT_RANGE_END);
            return;
        }
        if (!freePorts.add(port)) {
            logger.debug("Port {} was already free in the pool", port);
        }
    }
}
