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
package org.openhab.binding.boschthermotechnology.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ZoneThermostatConfiguration} class contains the configuration parameters of a single
 * {@code zone-thermostat} thing - one multi-zone RF room thermostat, identified by the gateway it
 * belongs to plus its own zone id (see ADR-005/ADR-006 and {@code myapp-api-analysis.md}'s
 * description of the {@code zones/zone{n}} resource tree).
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ZoneThermostatConfiguration {

    /**
     * The id of the {@code gateway} bridge this zone thermostat belongs to. Filled in
     * automatically by {@code ChildThingDiscoveryService}.
     */
    public String gatewayId = "";

    /**
     * The zone number (e.g. {@code "1"} for {@code zone1}), as returned by {@code zones/list}.
     * Filled in automatically by {@code ChildThingDiscoveryService}.
     */
    public String zoneId = "";

    /**
     * Poll interval in seconds for resource reads.
     */
    public int refreshInterval = 60;
}
