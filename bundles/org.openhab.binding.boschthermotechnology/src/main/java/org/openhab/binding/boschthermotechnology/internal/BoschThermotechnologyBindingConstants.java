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
package org.openhab.binding.boschthermotechnology.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link BoschThermotechnologyBindingConstants} class defines common constants, which are
 * used across the whole binding: thing type UIDs, channel ids, configuration/property keys,
 * PointT resource paths, and the SingleKey ID OAuth2 endpoints.
 *
 * <p>
 * The SingleKey ID / PointT API details reproduced here (client id, endpoints, resource paths)
 * are taken from the reverse-engineering analysis of the official MyBuderus/DashApp mobile app
 * (see {@code docs/CONCEPT.md}, ADR-002, ADR-005 and ADR-006) and are the public, non-secret
 * parameters of a PKCE-based public OAuth2 client.
 *
 * <p>
 * Since ADR-005/ADR-006, {@code gateway} is a bridge holding only box-level system channels; the
 * heating-circuit/DHW/heat-source/PV/pool/ventilation/zone-thermostat/energy-monitoring/AC/water
 * softener channels formerly bundled into one {@code gateway} thing are now split across eight
 * sibling child thing-types.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BoschThermotechnologyBindingConstants {

    private static final String BINDING_ID = "boschthermotechnology";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_GATEWAY = new ThingTypeUID(BINDING_ID, "gateway");
    public static final ThingTypeUID THING_TYPE_HEATPUMP = new ThingTypeUID(BINDING_ID, "heatpump");
    public static final ThingTypeUID THING_TYPE_PV = new ThingTypeUID(BINDING_ID, "pv");
    public static final ThingTypeUID THING_TYPE_POOL = new ThingTypeUID(BINDING_ID, "pool");
    public static final ThingTypeUID THING_TYPE_VENTILATION_ZONE = new ThingTypeUID(BINDING_ID, "ventilation-zone");
    public static final ThingTypeUID THING_TYPE_ZONE_THERMOSTAT = new ThingTypeUID(BINDING_ID, "zone-thermostat");
    public static final ThingTypeUID THING_TYPE_ENERGY_MONITORING = new ThingTypeUID(BINDING_ID, "energy-monitoring");
    public static final ThingTypeUID THING_TYPE_AC_UNIT = new ThingTypeUID(BINDING_ID, "ac-unit");
    public static final ThingTypeUID THING_TYPE_WATER_SOFTENER = new ThingTypeUID(BINDING_ID, "water-softener");

    // Gateway / child thing configuration / property keys
    public static final String CONFIG_GATEWAY_ID = "gatewayId";
    public static final String CONFIG_ZONE_ID = "zoneId";
    public static final String CONFIG_REFRESH_INTERVAL = "refreshInterval";
    public static final String PROPERTY_SERIAL_ID = "serialId";
    public static final String PROPERTY_FIRMWARE_VERSION = "firmwareVersion";
    public static final String PROPERTY_HARDWARE_VERSION = "hardwareVersion";

    /**
     * Account bridge configuration key: the user pastes the (unreachable) redirect URL from their
     * browser here after logging in at SingleKey ID, to complete the manual PKCE flow described in
     * ADR-002. The handler consumes and clears this value immediately - it is never persisted.
     */
    public static final String CONFIG_PASTE_REDIRECT_URL = "pasteAuthorizationRedirectUrl";

    /**
     * Account bridge configuration key: automatically filled in by {@code AccountBridgeHandler}
     * during {@code initialize()} with the SingleKey ID authorization URL the user must open in a
     * browser to log in. Not user-editable in practice, but exposed as a config parameter (rather
     * than a Thing property or status message) so it is easy to find and copy in any UI. Cleared
     * again once login succeeds.
     */
    public static final String CONFIG_AUTH_URL = "authUrl";

    // Channel ids - gateway system group
    public static final String CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE = "outdoor-temperature";
    public static final String CHANNEL_SYSTEM_AWAY_MODE_ENABLED = "away-mode-enabled";
    public static final String CHANNEL_SYSTEM_SILENT_MODE_ENABLED = "silent-mode-enabled";
    public static final String CHANNEL_SYSTEM_SEASON_OPTIMIZER_MODE = "season-optimizer-mode";
    public static final String CHANNEL_SYSTEM_HOLIDAY_MODE_ACTIVE = "holiday-mode-active";

    // Channel ids - gateway notifications group
    public static final String CHANNEL_NOTIFICATIONS_ACTIVE = "active";

    // Channel ids - heatpump / heating-circuit-{n} group
    public static final String CHANNEL_HC_MANUAL_ROOM_SETPOINT = "manual-room-setpoint";

    // Channel ids - heatpump / dhw-circuit-{n} group
    public static final String CHANNEL_DHW_CHARGE_DURATION = "charge-duration";
    public static final String CHANNEL_DHW_SINGLE_CHARGE_SETPOINT = "single-charge-setpoint";
    public static final String CHANNEL_DHW_OPERATION_MODE = "operation-mode";
    public static final String CHANNEL_DHW_CHARGE = "charge";
    public static final String CHANNEL_DHW_REDUCE_TEMP_ON_ALARM = "reduce-temp-on-alarm";

    // Channel ids - heatpump / heat-source-{n} group
    public static final String CHANNEL_HEAT_SOURCE_CH_STATUS = "ch-status";
    public static final String CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE = "actual-supply-temperature";
    public static final String CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE = "return-temperature";
    public static final String CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS = "number-of-starts";
    public static final String CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM = "working-time-total-system";

    // Channel ids - pv / photovoltaic group
    public static final String CHANNEL_PV_ENABLED = "enabled";
    public static final String CHANNEL_PV_INVERTER_INFO = "inverter-info";

    // Channel ids - pv / solar-thermal group
    public static final String CHANNEL_SOLAR_COLLECTOR_TEMPERATURE = "collector-temperature";
    public static final String CHANNEL_SOLAR_YIELD = "yield";

    // Channel ids - pool
    public static final String CHANNEL_POOL_ENABLED = "enabled";
    public static final String CHANNEL_POOL_SETPOINT_TEMPERATURE = "setpoint-temperature";
    public static final String CHANNEL_POOL_CURRENT_TEMPERATURE = "current-temperature";

    // Channel ids - ventilation-zone
    public static final String CHANNEL_VENTILATION_OPERATION_MODE = "operation-mode";
    public static final String CHANNEL_VENTILATION_FILTER_REMAINING_TIME = "filter-remaining-time";

    // Channel ids - zone-thermostat
    public static final String CHANNEL_ZONE_MANUAL_ROOM_SETPOINT = "manual-room-setpoint";
    public static final String CHANNEL_ZONE_AVERAGE_CURRENT_TEMPERATURE = "average-current-temperature";
    public static final String CHANNEL_ZONE_CHILD_LOCK = "child-lock";

    // Channel ids - energy-monitoring
    public static final String CHANNEL_ENERGY_ACTUAL_CH_POWER = "actual-ch-power";
    public static final String CHANNEL_ENERGY_ACTUAL_DHW_POWER = "actual-dhw-power";
    public static final String CHANNEL_ENERGY_TOTAL_CONSUMED_ENERGY = "total-consumed-energy";

    // Channel ids - ac-unit
    public static final String CHANNEL_AC_OPERATION_MODE = "operation-mode";
    public static final String CHANNEL_AC_FAN_SPEED = "fan-speed";
    public static final String CHANNEL_AC_TEMPERATURE_SETPOINT = "temperature-setpoint";

    /**
     * PointT resource paths. The heating-circuit/DHW paths were confirmed against the
     * reverse-engineering analysis ({@code buderus-reverse.md}, section 1); the heat-source and
     * notifications paths, and the exact DHW operation mode option values (see
     * {@code dhw-operation-mode} in {@code thing-types.xml}), were confirmed against a real
     * capture from a tested K30/MX300/MX400 gateway (see the {@code buderus-main} project's
     * README "Status" section). Note that {@code heatSources} paths are flat
     * ({@code /heatSources/...}), unlike {@code heatingCircuits}/{@code dhwCircuits} which nest
     * under a sub-id ({@code hc1}/{@code dhw1}) - the tested gateway did not expose an
     * {@code hs1}-style sub-id for heat sources, so {@link #HEAT_SOURCE_ID_DEFAULT} is used as the
     * single heat-source-group instance id until a cascade system (multiple heat sources) is
     * captured and this needs generalizing the same way heating/DHW circuits already are (see
     * ADR-006).
     *
     * <p>
     * The remaining paths (PV, pool, ventilation, zone thermostats, energy monitoring, RAC) come
     * from the wider MyBuderus Android app analysis ({@code myapp-api-analysis.md}) and have not
     * been confirmed against a live gateway - each corresponding channel type in
     * {@code thing-types.xml} carries a TODO noting this where the exact value shape matters.
     */
    public static final class ResourcePaths {

        // Gateway system (flat, one instance per gateway)
        public static final String OUTDOOR_TEMPERATURE = "/system/sensors/temperatures/outdoor_t1";
        public static final String AWAY_MODE_ENABLED = "/system/awayMode/enabled";
        public static final String SILENT_MODE_ENABLED = "/system/silentMode/enabled";
        public static final String SEASON_OPTIMIZER_MODE = "/system/seasonOptimizer/mode";
        public static final String HOLIDAY_MODE_ACTIVE = "/holidayMode/activeModes";
        public static final String GATEWAY_SERIAL_ID = "/gateway/serialId";
        public static final String GATEWAY_FIRMWARE_VERSION = "/gateway/versionFirmware";
        public static final String GATEWAY_HARDWARE_VERSION = "/gateway/versionHardware";
        public static final String NOTIFICATIONS = "/notifications";

        // Heatpump: list endpoints used by HeatpumpHandler/ChildThingDiscoveryService to enumerate circuits
        public static final String HEATING_CIRCUITS_LIST = "/heatingCircuits";
        public static final String DHW_CIRCUITS_LIST = "/dhwCircuits";

        /**
         * Templates take a circuit id (e.g. {@code "hc1"}, {@code "dhw1"}) via
         * {@link String#format(String, Object...)}, substituted by {@code HeatpumpHandler} for
         * each circuit discovered on {@link #HEATING_CIRCUITS_LIST}/{@link #DHW_CIRCUITS_LIST} -
         * generalizing the single hardcoded hc1/dhw1 paths this binding used before ADR-006.
         */
        public static final String MANUAL_ROOM_SETPOINT_TEMPLATE = "/heatingCircuits/%s/manualRoomSetpoint";
        public static final String DHW_CHARGE_DURATION_TEMPLATE = "/dhwCircuits/%s/chargeDuration";
        public static final String DHW_SINGLE_CHARGE_SETPOINT_TEMPLATE = "/dhwCircuits/%s/singleChargeSetpoint";
        public static final String DHW_OPERATION_MODE_TEMPLATE = "/dhwCircuits/%s/operationMode";
        public static final String DHW_CHARGE_TEMPLATE = "/dhwCircuits/%s/charge";
        public static final String DHW_REDUCE_TEMP_ON_ALARM_TEMPLATE = "/dhwCircuits/%s/reduceTempOnAlarm";

        /**
         * The only heat-source-group instance id used until a cascade system confirms a
         * {@code hs1}/{@code hs2}-style sub-id shape - see the class Javadoc above.
         */
        public static final String HEAT_SOURCE_ID_DEFAULT = "1";
        public static final String HEAT_SOURCE_CH_STATUS = "/heatSources/chStatus";
        public static final String HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE = "/heatSources/actualSupplyTemperature";
        public static final String HEAT_SOURCE_RETURN_TEMPERATURE = "/heatSources/returnTemperature";
        public static final String HEAT_SOURCE_NUMBER_OF_STARTS = "/heatSources/numberOfStarts";
        public static final String HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM = "/heatSources/workingTime/totalSystem";

        // PV: list endpoint used by ChildThingDiscoveryService; sc1 is assumed as the single
        // solar-circuit instance id, analogous to HEAT_SOURCE_ID_DEFAULT above, until a
        // multi-solar-circuit installation is captured.
        public static final String PV_LIST = "/pv/list";
        public static final String SOLAR_CIRCUITS_LIST = "/solarCircuits";
        public static final String PV_ENABLED = "/pv/enable";
        public static final String PV_INVERTER_INFO = "/pv/commissioning/inverterInfo";
        public static final String SOLAR_CIRCUIT_ID_DEFAULT = "sc1";
        public static final String SOLAR_COLLECTOR_TEMPERATURE_TEMPLATE = "/solarCircuits/%s/collectorTemperature";
        public static final String SOLAR_YIELD_TEMPLATE = "/solarCircuits/%s/solarYield";

        // Pool: probed for existence by ChildThingDiscoveryService, no list endpoint exists
        public static final String POOL_ENABLED = "/pool/enabled";
        public static final String POOL_SETPOINT_TEMPERATURE = "/pool/setpointTemp";
        public static final String POOL_CURRENT_TEMPERATURE = "/pool/currentTemp";

        // Ventilation zone: probed for existence, hardcoded to zone1 (the only zone id ever
        // observed in the app analysis - see ADR-006)
        public static final String VENTILATION_OPERATION_MODE = "/ventilation/zone1/operationMode";
        public static final String VENTILATION_FILTER_REMAINING_TIME = "/ventilation/zone1/filter/remainingTime";

        // Multi-zone RF thermostats: list endpoint used by ChildThingDiscoveryService
        public static final String ZONES_LIST = "/zones/list";

        /**
         * Templates take a zone id (e.g. {@code "1"} for {@code zone1}) via
         * {@link String#format(String, Object...)}. {@code heatCoolMode} is hardcoded to
         * {@code "heat"} - see the TODO on {@code zone-manual-room-setpoint} in
         * {@code thing-types.xml}.
         */
        public static final String ZONE_MANUAL_ROOM_SETPOINT_TEMPLATE = "/zones/zone%s/heat/manualRoomSetpoint";
        public static final String ZONE_AVERAGE_CURRENT_TEMPERATURE_TEMPLATE = "/zones/zone%s/averageCurrentTemperature";
        public static final String ZONE_CHILD_LOCK_TEMPLATE = "/zones/zone%s/childLock";

        // Energy monitoring - see the time-series TODO on the corresponding channel types
        public static final String ENERGY_ACTUAL_CH_POWER = "/recordings/heatSources/actualCHPower";
        public static final String ENERGY_ACTUAL_DHW_POWER = "/recordings/heatSources/actualDHWPower";
        public static final String ENERGY_TOTAL_CONSUMED_ENERGY = "/recordings/heatSources/total/energyMonitoring/consumedEnergy";

        // AC unit (RAC): probed for existence via AC_STANDARD_FUNCTIONS by ChildThingDiscoveryService
        public static final String AC_STANDARD_FUNCTIONS = "/airConditioning/standardFunctions";
        public static final String AC_OPERATION_MODE = "/airConditioning/operationMode";
        public static final String AC_FAN_SPEED = "/airConditioning/fanSpeed";
        public static final String AC_TEMPERATURE_SETPOINT = "/airConditioning/temperatureSetpoint";

        private ResourcePaths() {
            // constants only
        }
    }

    /**
     * SingleKey ID OAuth2/OIDC endpoints and the public DashApp client id used for the
     * Authorization Code + PKCE (S256) login. See ADR-002 for the full flow description.
     */
    public static final class Oidc {

        public static final String AUTHORIZATION_URL = "https://singlekey-id.com/auth/connect/authorize";
        public static final String TOKEN_URL = "https://singlekey-id.com/auth/connect/token";

        /** Public client id of the Bosch DashApp - not a secret, PKCE public-client flow. */
        public static final String CLIENT_ID = "762162C0-FA2D-4540-AE66-6489F189FADC";

        /**
         * Full DashApp scope list, exactly as documented in {@code buderus-reverse.md} section 2
         * step 2. The reduced scope originally proposed in ADR-002 (only
         * {@code openid offline_access pointt.gateway.list pointt.gateway.resource.dashapp}) was
         * tested against a real SingleKey ID login and rejected outright with a
         * "Misconfigured Application" error page - before any login form was even shown, i.e. an
         * app/scope registration check at the identity provider, not a runtime auth error. See the
         * ADR-002 update for details.
         */
        public static final String SCOPE = "openid email profile offline_access pointt.gateway.claiming pointt.gateway.removal pointt.gateway.list pointt.gateway.users pointt.gateway.resource.dashapp pointt.castt.flow.token-exchange bacon hcc.tariff.read";

        private Oidc() {
            // constants only
        }
    }

    /** Base URL of the PointT API (gateway/resource operations). */
    public static final String POINTT_API_BASE_URL = "https://pointt-api.bosch-thermotechnology.com/pointt-api/api/v1";

    private BoschThermotechnologyBindingConstants() {
        // constants only
    }
}
