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
package org.openhab.binding.eebus.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EEBusOhPeerConfiguration} class contains the configuration parameters of the
 * {@code eebus:oh-peer} Thing, i.e. of exactly one openHAB-managed pairing with a remote EEBUS
 * device.
 *
 * <p>
 * Pairing in this binding follows the standard openHAB Bridge/Thing convention: creating
 * an {@code eebus:oh-peer} Thing under an {@code eebus:service} Bridge <strong>is</strong> the
 * act of pairing — its {@link #ski} is added to the Bridge's trusted-SKI set (see
 * {@code EEBusHandler#recomputeTrustedSkis()}). Removing the Thing revokes trust.
 * There is deliberately no separate "approve/reject" action (see CONCEPT.md §6 decision 4:
 * no PIN pairing, and §5.2: pairing == Thing lifecycle, not a bespoke API).
 * </p>
 *
 * <p>
 * <strong>Origin (CONCEPT.md §4.5):</strong> split out of the former single {@code eebus:peer}
 * Thing type, which used to double as both this (openHAB-managed pairing, under
 * {@code eebus:service}) and a passive real-device record (under {@code eebus:network}, now
 * {@code EEBusPeerConfiguration}, which has no {@link #shipId} since it never performs a
 * handshake).
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusOhPeerConfiguration {

    /**
     * Subject Key Identifier (SKI) of the remote device, 40 lowercase hex characters.
     * For v1, this must be entered manually (e.g. copied from an {@code eebus:peer} Thing
     * populated by discovery, or read from the remote device's own display/QR code per SHIP
     * Installation Process) — see CONCEPT.md §4.4/§4.5 for why there is no automatic link
     * between an {@code eebus:peer} and this Thing yet.
     */
    public String ski = "";

    /**
     * SHIP-ID of the remote device. Empty until learned during the first successful
     * handshake; the handler persists it here via {@code updateConfiguration()} once
     * known, so it does not need to be re-learned on every restart.
     */
    public String shipId = "";
}
