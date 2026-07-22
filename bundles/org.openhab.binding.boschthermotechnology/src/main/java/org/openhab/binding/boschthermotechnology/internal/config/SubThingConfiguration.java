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
 * The {@link SubThingConfiguration} class contains the configuration parameters shared by every
 * child thing-type of a {@code gateway} bridge that does not need any further identifier beyond
 * the gateway itself - {@code heatpump}, {@code pv}, {@code pool}, {@code ventilation-zone},
 * {@code energy-monitoring}, {@code ac-unit}, and {@code water-softener} (see ADR-005/ADR-006).
 * {@code zone-thermostat} additionally needs a zone id and uses
 * {@link ZoneThermostatConfiguration} instead.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class SubThingConfiguration {

    /**
     * The id of the {@code gateway} bridge this thing belongs to. Filled in automatically by
     * {@code ChildThingDiscoveryService}, but can also be entered manually if the thing is added
     * without discovery.
     */
    public String gatewayId = "";

    /**
     * Poll interval in seconds for resource reads.
     */
    public int refreshInterval = 60;
}
