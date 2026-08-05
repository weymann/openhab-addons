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
 * {@code eebus:peer} Thing, i.e. of exactly one "real" EEBus device seen on the network.
 *
 * <p>
 * <strong>Revised (CONCEPT.md §4.5):</strong> this Thing is a child of {@code eebus:network}
 * only, which holds no SHIP/SPINE identity of its own - there is nothing for it to pair
 * against here, so adding this Thing does <strong>not</strong> establish trust and it never
 * performs a SHIP handshake (hence no {@code shipId} field - that only applies once a device
 * is actually paired, see {@code EEBusOhPeerConfiguration}). It is a passive record, ideally
 * populated by {@code EEBusMdnsDiscoveryParticipant}'s Inbox suggestions, of a device seen on
 * the network. Pairing (trust) happens by creating an {@code eebus:oh-peer} Thing under an
 * {@code eebus:service} Bridge instead - see {@code EEBusOhPeerConfiguration}.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusPeerConfiguration {

    /**
     * Subject Key Identifier (SKI) of the remote device, 40 lowercase hex characters.
     * Ideally taken over from an {@code EEBusMdnsDiscoveryParticipant} Inbox suggestion; can
     * also be entered manually (e.g. read from the remote device's own display/QR code per
     * SHIP Installation Process).
     */
    public String ski = "";
}
