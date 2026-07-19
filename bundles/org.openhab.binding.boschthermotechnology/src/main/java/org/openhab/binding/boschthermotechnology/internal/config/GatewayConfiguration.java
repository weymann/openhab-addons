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
 * The {@link GatewayConfiguration} class contains the configuration parameters of a single
 * {@code gateway} Thing. A gateway is identified by the id the PointT API assigns to it; this
 * value is filled in automatically by {@code GatewayDiscoveryService} but can also be entered
 * manually if the gateway is added without discovery.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GatewayConfiguration {

    /**
     * The gateway id as returned by {@code GET /gateways/} on the PointT API.
     */
    public String gatewayId = "";

    /**
     * Poll interval in seconds for resource reads, matching the 60 s default used by the
     * reference implementation ({@code buderus-reverse.md}, section 1).
     */
    public int refreshInterval = 60;
}
