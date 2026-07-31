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

/**
 * The "superordinate binding status" for one {@code eebus:oh-device}/{@code eebus:oh-cs-device}
 * Bridge - see docs/ADR/043-topology-phase-gate.md. Persisted per-Bridge in openHAB's
 * {@code StorageService} (mirroring the {@code keystoreStorage} pattern - see
 * {@link EEBusHandler}'s keystore persistence helpers), independently of the per-
 * {@code initialize()} generation counter described in {@link EEBusHandler}'s class javadoc and
 * ADR-005.
 *
 * <p>
 * This does not fix the underlying causes behind repeated {@code initialize()}/full-Device-
 * rebuild churn (ADR-005's still-open "why does initialize() run more than once" question, nor
 * the {@code jeebus.spine} client-side subscription-cache desync documented in project memory
 * as of 2026-09-04) - it only removes this Bridge's exposure to that churn during the setup/
 * change phase, by not doing any SPINE connection/negotiation work at all until a human
 * explicitly confirms the topology (which Bridges, which attached {@code eebus:oh-entity}
 * Things) is as intended.
 * </p>
 */
@NonNullByDefault
public enum EEBusTopologyPhase {

    /**
     * Default state, for a brand-new Bridge and for any Bridge that has not yet been explicitly
     * settled. {@link EEBusHandler#initialize()} does not reserve a port or attempt any SPINE
     * connection/negotiation while in this state, no matter how many times {@code initialize()}
     * itself runs (config edits, child {@code eebus:oh-entity} Things attaching, openHAB's own
     * ADR-005-documented duplicate-{@code initialize()} calls, ...): all of that is now free to
     * happen during setup/reconfiguration without ever touching the network.
     */
    ASSEMBLING,

    /**
     * Set once, explicitly, via the {@code settleTopology()} Thing Action
     * ({@link EEBusTopologyActions}) on the {@code oh-device} Bridge - never automatically.
     * Persists across restarts: once a Bridge is {@code SETTLED}, every future
     * {@link EEBusHandler#initialize()} (including after a restart) proceeds exactly as it did
     * before this mechanism existed - resolving a port and starting SHIP/SPINE.
     */
    SETTLED
}
