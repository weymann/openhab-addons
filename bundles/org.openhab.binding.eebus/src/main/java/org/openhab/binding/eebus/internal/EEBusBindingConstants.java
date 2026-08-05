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
package org.openhab.binding.eebus.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EEBusBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusBindingConstants {

    private static final String BINDING_ID = "eebus";

    // List of all Thing Type UIDs
    /** Bridge Thing: one local SHIP/SPINE service instance. */
    public static final ThingTypeUID THING_TYPE_SERVICE = new ThingTypeUID(BINDING_ID, "service");
    /**
     * Bridge Thing: lightweight anchor with no mandatory configuration, parent of discovered
     * {@code eebus:peer} Inbox entries (ADR-003). Holds no SHIP/SPINE identity itself.
     */
    public static final ThingTypeUID THING_TYPE_NETWORK = new ThingTypeUID(BINDING_ID, "network");
    /**
     * A "real" EEBus device seen on the network, identified by its SKI. Child of
     * {@link #THING_TYPE_NETWORK} only - carries no channels and does not establish trust. See
     * CONCEPT.md §4.5.
     */
    public static final ThingTypeUID THING_TYPE_PEER = new ThingTypeUID(BINDING_ID, "peer");
    /**
     * An openHAB-managed pairing with a real EEBus device, identified by its SKI. Child of
     * {@link #THING_TYPE_SERVICE} only - creating this Thing performs pairing (CONCEPT.md
     * §5.2/§4.5).
     */
    public static final ThingTypeUID THING_TYPE_OH_PEER = new ThingTypeUID(BINDING_ID, "oh-peer");

    /**
     * mDNS service type for SHIP 7.3.2 device announcements. Shared between
     * {@code EEBusMdnsBrowser} (runtime session bookkeeping, scoped to an active
     * {@code eebus:service}) and {@code EEBusMdnsDiscoveryParticipant} (Inbox population,
     * binding-scoped) - see ADR-003.
     */
    public static final String SERVICE_TYPE_SHIP_MDNS = "_ship._tcp.local.";

    /**
     * Thing property key an {@code eebus:service} Bridge publishes its own SKI under, once its
     * SHIP server is up ({@code EEBusHandler#startShipSpine}). Also read by
     * {@code EEBusMdnsDiscoveryParticipant} to recognize and exclude a Bridge's own mDNS
     * self-announcement from the Inbox - see ADR-008.
     */
    public static final String PROPERTY_LOCAL_SKI = "localSki";

    /**
     * Thing property key an {@code eebus:oh-peer} Thing records its pairing state under: present
     * with value {@code "true"} once its {@code pair()} Thing Action has been invoked and not
     * since undone by {@code unpair()}; absent otherwise (including before {@code pair()} has
     * ever been invoked). Read by {@code EEBusHandler#currentPairedOhPeerSkis()} to decide which
     * child {@code eebus:oh-peer} Things contribute to the parent Bridge's trusted-SKI set -
     * Thing existence alone no longer implies trust. See docs/ADR/012-pairing-trust-property-and-
     * actions.md and CONCEPT.md §4.6.
     */
    public static final String PROPERTY_PAIRED = "paired";
}
