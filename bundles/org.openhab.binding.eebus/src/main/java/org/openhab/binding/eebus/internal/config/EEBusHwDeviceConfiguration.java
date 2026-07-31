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
 * The {@link EEBusHwDeviceConfiguration} class contains the configuration parameters of the
 * {@code eebus:hw-device} Thing, i.e. of exactly one "real" EEBus device seen on the network.
 *
 * <p>
 * <strong>Revised (CONCEPT.md §4.5/§7(15), docs/ADR/020-merge-network-peer-things.md):</strong>
 * this Thing is bridgeless (top-level) - formerly a child of a now-removed {@code eebus:network}
 * anchor Bridge, which held no SHIP/SPINE identity of its own. There is nothing for it to trust
 * against, so adding this Thing does <strong>not</strong> establish trust and it never
 * performs a SHIP handshake (hence no {@code shipId} field - that only applies once a device
 * is actually trusted, see {@code EEBusOhEntityConfiguration}). It is a passive record, ideally
 * populated by {@code EEBusMdnsDiscoveryParticipant}'s Inbox suggestions, of a device seen on
 * the network. Trust happens by adding this device's SKI to an {@code eebus:oh-device}
 * Bridge's {@code trustedSkis} Thing config (docs/ADR/027-derive-local-use-cases-from-
 * entities.md - the {@code eebus:oh-cs-device} Bridge this used to also apply to, and the
 * live {@code trust()} Thing Action, are both removed), then representing one of its SPINE
 * Entities via an {@code eebus:oh-entity} Thing - see
 * {@code EEBusOhEntityConfiguration}.
 * </p>
 *
 * <p>
 * <strong>Renamed (docs/ADR/024-oh-device-oh-entity-rename.md):</strong> this class was
 * {@code EEBusPeerConfiguration} - renamed to distinguish it clearly from openHAB-managed/
 * simulated concepts, matching the {@code eebus:eebus-peer} -&gt; {@code eebus:hw-device}
 * Thing-type rename.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusHwDeviceConfiguration {

    /**
     * Subject Key Identifier (SKI) of the remote device, 40 lowercase hex characters.
     * Ideally taken over from an {@code EEBusMdnsDiscoveryParticipant} Inbox suggestion; can
     * also be entered manually (e.g. read from the remote device's own display/QR code per
     * SHIP Installation Process).
     */
    public String ski = "";
}
