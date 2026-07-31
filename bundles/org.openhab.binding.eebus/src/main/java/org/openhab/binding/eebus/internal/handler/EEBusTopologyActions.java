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
package org.openhab.binding.eebus.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * Thing Actions for the {@code eebus:oh-device}/{@code eebus:oh-cs-device} Bridge - currently
 * just {@link #settleTopology()}, the manual trigger that moves a Bridge from
 * {@link EEBusTopologyPhase#ASSEMBLING} to {@link EEBusTopologyPhase#SETTLED} (see
 * docs/ADR/043-topology-phase-gate.md).
 *
 * <p>
 * This is the binding's first live Thing Actions class since {@code EEBusDeviceActions} (the
 * old {@code trust()}/{@code untrust()} actions) was removed by
 * docs/ADR/027-derive-local-use-cases-from-entities.md Decision 6 - that removal's reasoning
 * ("every config change is now uniformly a full rebuild, so no live Actions class is needed any
 * more") does not apply here: settling the topology is deliberately a distinct, human-triggered
 * step that config changes must NOT trigger by themselves (that is the entire point of
 * ADR-043), so a live Action is genuinely needed again.
 * </p>
 *
 * <p>
 * <strong>Verification note:</strong> written without a local compiler available (see project
 * memory/ADR process) - the {@link ThingActions}/{@link ThingActionsScope}/{@link RuleAction}
 * API used here has been stable and unchanged across openHAB core for years and is used by this
 * exact pattern in numerous other bindings, but has not been build-verified against the pinned
 * openHAB core version in this project; please double-check on first compile.
 * </p>
 */
@ThingActionsScope(name = "eebus")
@NonNullByDefault
public class EEBusTopologyActions implements ThingActions {

    private @Nullable EEBusHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof EEBusHandler eeBusHandler) {
            this.handler = eeBusHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    /**
     * Moves this Bridge from {@link EEBusTopologyPhase#ASSEMBLING} to
     * {@link EEBusTopologyPhase#SETTLED} (persisted, survives restarts - see
     * {@link EEBusHandler#settleTopology()}) and, if it was still {@code ASSEMBLING}, starts the
     * SPINE connection/negotiation now instead of waiting for some future {@code initialize()}
     * call. A no-op if this Bridge is already {@code SETTLED}.
     */
    @RuleAction(label = "Settle EEBus Topology", description = "Confirms this Bridge's current "
            + "topology (which Bridges, which attached entities) is as intended, and starts the "
            + "SPINE connection/negotiation. Only needed once per Bridge - persists across " + "restarts.")
    public void settleTopology() {
        EEBusHandler localHandler = handler;
        if (localHandler == null) {
            return;
        }
        localHandler.settleTopology();
    }

    /**
     * Static invocation helper for rules (DSL/JS/etc.), following the standard openHAB Thing
     * Actions pattern - see {@link #settleTopology()}.
     *
     * @param actions the {@link ThingActions} instance obtained via
     *            {@code getActions("eebus", "<bridgeThingUID>")}
     */
    public static void settleTopology(ThingActions actions) {
        if (actions instanceof EEBusTopologyActions eebusActions) {
            eebusActions.settleTopology();
        } else {
            throw new IllegalArgumentException("Instance is not an EEBusTopologyActions class.");
        }
    }

    /**
     * Moves this Bridge back from {@link EEBusTopologyPhase#SETTLED} to
     * {@link EEBusTopologyPhase#ASSEMBLING} - the reverse of {@link #settleTopology()}. Added
     * (see docs/ADR/043-topology-phase-gate.md, "Update 2026-09-04") specifically as a
     * development/testing convenience: lets a Bridge be re-gated (stopping any live SHIP/SPINE
     * connection) without disabling/re-enabling the Thing or restarting openHAB. A no-op if this
     * Bridge is already {@code ASSEMBLING} - see {@link EEBusHandler#unsettleTopology()}.
     */
    @RuleAction(label = "Unsettle EEBus Topology", description = "Development/testing convenience: "
            + "moves this Bridge back to ASSEMBLING and stops its SPINE connection, without "
            + "disabling/re-enabling the Thing or restarting openHAB. The reverse of " + "settleTopology().")
    public void unsettleTopology() {
        EEBusHandler localHandler = handler;
        if (localHandler == null) {
            return;
        }
        localHandler.unsettleTopology();
    }

    /**
     * Static invocation helper for rules (DSL/JS/etc.) - see {@link #unsettleTopology()}.
     *
     * @param actions the {@link ThingActions} instance obtained via
     *            {@code getActions("eebus", "<bridgeThingUID>")}
     */
    public static void unsettleTopology(ThingActions actions) {
        if (actions instanceof EEBusTopologyActions eebusActions) {
            eebusActions.unsettleTopology();
        } else {
            throw new IllegalArgumentException("Instance is not an EEBusTopologyActions class.");
        }
    }
}
