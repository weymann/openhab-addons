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
package org.openhab.binding.energychartsinfo.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EnergyChartsInfoBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EnergyChartsInfoBindingConstants {

    private static final String BINDING_ID = "energychartsinfo";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ENERGY_CHARTS_INFO = new ThingTypeUID(BINDING_ID, "energychartsinfo");

    public static final String CHARTS_INFO_URL = "https://api.energy-charts.info/";
    public static final String ENERGY_FORECAST_URL = "https://www.energyforecast.de/api/v1/predictions/next_96_hours";

    public static final String CHANNEL_GROUP_PRICE = "price";
    public static final String CHANNEL_GROUP_RENEWABLES = "renewables";

    public static final String CHANNEL_DAY_AHEAD = "day-ahead";
    public static final String CHANNEL_FORECAST = "forecast";

    public static final String CHANNEL_REN_SHARE = "total";
    public static final String CHANNEL_SOLAR_SHARE = "solar";
    public static final String CHANNEL_WIND_ONSHORE_SHARE = "wind-onshore";
    public static final String CHANNEL_WIND_OFFSHORE_SHARE = "wind-offshore";
}
