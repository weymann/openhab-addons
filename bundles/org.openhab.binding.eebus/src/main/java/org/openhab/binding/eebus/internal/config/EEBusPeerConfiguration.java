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
 * The {@link EEBusPeerConfiguration} class contains the configuration parameters of the
 * {@code eebus:peer} Thing, i.e. of exactly one paired remote EEBUS device.
 *
 * <p>
 * Pairing in this binding follows the standard openHAB Bridge/Thing convention: creating
 * an {@code eebus:peer} Thing under a Bridge <strong>is</strong> the act of pairing — its
 * {@link #ski} is added to the Bridge's trusted-SKI set (see
 * {@code EEBusBridgeHandler#recomputeTrustedSkis()}). Removing the Thing revokes trust.
 * There is deliberately no separate "approve/reject" action (see CONCEPT.md §6 decision 4:
 * no PIN pairing, and §5.2: pairing == Thing lifecycle, not a bespoke API).
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusPeerConfiguration {

    /**
     * Subject Key Identifier (SKI) of the remote device, 40 lowercase hex characters.
     * For v1, this must be entered manually (e.g. read from the remote device's own
     * display/QR code per SHIP Installation Process) — see CONCEPT.md §6 for why an
     * auto-discovery inbox of unpaired peers is deferred.
     *
     * TODO(CONCEPT §7.4): decide whether to request a ShipCommunication extension (raw mDNS
     * events) to enable a real discovery inbox, or keep manual SKI entry permanently.
     */
    public String ski = "";

    /**
     * SHIP-ID of the remote device. Empty until learned during the first successful
     * handshake; the handler persists it here via {@code updateConfiguration()} once
     * known, so it does not need to be re-learned on every restart.
     */
    public String shipId = "";
}
