/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.measure.Unit;
import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.interfaces.Battery;
import org.openhab.binding.curves.internal.interfaces.Grid;
import org.openhab.binding.curves.internal.interfaces.Household;
import org.openhab.binding.curves.internal.interfaces.PV;
import org.openhab.binding.curves.internal.interfaces.Wallbox;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link CurvesConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class CurvesConstants {

    public static final String BINDING_ID = "curves";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_HEMS = new ThingTypeUID(BINDING_ID, "hems");
    public static final ThingTypeUID THING_TYPE_PV = new ThingTypeUID(BINDING_ID, "pv");
    public static final ThingTypeUID THING_TYPE_HOUSEHOLD = new ThingTypeUID(BINDING_ID, "household");
    public static final ThingTypeUID THING_TYPE_GRID = new ThingTypeUID(BINDING_ID, "grid");
    public static final ThingTypeUID THING_TYPE_BATTERY = new ThingTypeUID(BINDING_ID, "battery");
    public static final ThingTypeUID THING_TYPE_WALLBOX = new ThingTypeUID(BINDING_ID, "wallbox");

    // config uri's
    public static final String HEMS_CONFIG_URI = "thing-type:" + THING_TYPE_HEMS.getAsString();
    public static final String PV_CONFIG_URI = "thing-type:" + THING_TYPE_PV.getAsString();
    public static final String HOUSEHOLD_CONFIG_URI = "thing-type:" + THING_TYPE_HOUSEHOLD.getAsString();
    public static final String GRID_CONFIG_URI = "thing-type:" + THING_TYPE_GRID.getAsString();
    public static final String BATTERY_CONFIG_URI = "thing-type:" + THING_TYPE_BATTERY.getAsString();

    // config item names
    public static final String CONFIG_ITEM_DEFAULT_PERSISTENCE = "defaultPersistence";
    public static final String CONFIG_ITEM_PV_POWER_PERSISTENCE = "pvPowerItemPersistence";
    public static final String CONFIG_ITEM_PV_POWER_FORECAST_PERSISTENCE = "powerForecastItemPersistence";
    public static final String CONFIG_ITEM_HOUSEHOLD_POWER_PERSISTENCE = "householdPowerItemPersistence";
    public static final String CONFIG_ITEM_HOME2GRID_POWER_PERSISTENCE = "home2GridItemPersistence";
    public static final String CONFIG_ITEM_GRID2HOME_POWER_PERSISTENCE = "grid2HomeItemPersistence";
    public static final String CONFIG_ITEM_BATTERY_SOC_POWER_PERSISTENCE = "stateOfChargeItemPersistence";
    public static final String CONFIG_ITEM_BATTERY_CHARGE_POWER_PERSISTENCE = "chargeItemPersistence";
    public static final String CONFIG_ITEM_BATTERY_DISCHARGE_POWER_PERSISTENCE = "dischargeItemPersistence";

    public static final String CHANNEL_HOUSEHOLD_CAST = "household-cast";
    public static final String CHANNEL_TO_GRID_CAST = "to-grid-cast";
    public static final String CHANNEL_FROM_GRID_CAST = "from-grid-cast";
    public static final String CHANNEL_CHARGE_CAST = "charge-cast";
    public static final String CHANNEL_DISCHARGE_CAST = "discharge-cast";

    public static final List<Class<?>> SUPPORTED_INTERFACES = Arrays.asList(Household.class, PV.class, Battery.class,
            Grid.class, Wallbox.class);
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_HEMS, THING_TYPE_PV,
            THING_TYPE_BATTERY, THING_TYPE_GRID, THING_TYPE_HOUSEHOLD, THING_TYPE_WALLBOX);
    public static final Unit<Power> KILOWATT_UNIT = MetricPrefix.KILO(Units.WATT);
}
