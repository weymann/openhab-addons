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
package org.openhab.binding.brightsky.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Defines common constants used across the BrightSky binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public final class BrightSkyBindingConstants {

    public static final String BINDING_ID = "brightsky";

    // Thing type
    public static final ThingTypeUID THING_TYPE_WEATHER_LOCATION = new ThingTypeUID(BINDING_ID, "weather-location");

    // Channel group
    public static final String GROUP_CURRENT = "current";

    // Channel IDs (within group "current")
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_DEW_POINT = "dew-point";
    public static final String CHANNEL_HUMIDITY = "humidity";
    public static final String CHANNEL_PRESSURE = "pressure";
    public static final String CHANNEL_WIND_SPEED = "wind-speed";
    public static final String CHANNEL_WIND_DIRECTION = "wind-direction";
    public static final String CHANNEL_WIND_GUST_SPEED = "wind-gust-speed";
    public static final String CHANNEL_WIND_GUST_DIRECTION = "wind-gust-direction";
    public static final String CHANNEL_PRECIPITATION = "precipitation";
    public static final String CHANNEL_CLOUD_COVER = "cloud-cover";
    public static final String CHANNEL_VISIBILITY = "visibility";
    public static final String CHANNEL_SUNSHINE = "sunshine";
    public static final String CHANNEL_SOLAR = "solar";
    public static final String CHANNEL_CONDITION = "condition";
    public static final String CHANNEL_ICON = "icon";
    public static final String CHANNEL_OBSERVATION_TIME = "observation-time";

    // Configuration parameter keys
    public static final String CONFIG_LATITUDE = "latitude";
    public static final String CONFIG_LONGITUDE = "longitude";
    public static final String CONFIG_STATION_ID = "stationId";
    public static final String CONFIG_REFRESH_INTERVAL = "refreshInterval";

    private BrightSkyBindingConstants() {
        // Utility class - prevent instantiation
    }
}
