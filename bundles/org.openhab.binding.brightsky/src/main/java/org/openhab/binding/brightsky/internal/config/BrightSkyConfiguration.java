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
package org.openhab.binding.brightsky.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Configuration POJO for the {@code brightsky:weather-location} Thing.
 * Bound via {@code getConfigAs(BrightSkyConfiguration.class)}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BrightSkyConfiguration {

    /** Geographic latitude of the location (-90 to 90). Required unless {@link #stationId} is set. */
    public double latitude;

    /** Geographic longitude of the location (-180 to 180). Required unless {@link #stationId} is set. */
    public double longitude;

    /**
     * Optional DWD station ID. When set, overrides lat/lon for station selection.
     * Example: "00433" for Berlin-Tempelhof.
     */
    public @Nullable String stationId;

    /** Polling interval in minutes for current weather. Default: 30. */
    public int refreshInterval = 30;
}
