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
 * (see {@code docs/CONCEPT.md} and ADR-002) and are the public, non-secret parameters of a
 * PKCE-based public OAuth2 client.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BoschThermotechnologyBindingConstants {

    private static final String BINDING_ID = "boschthermotechnology";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_GATEWAY = new ThingTypeUID(BINDING_ID, "gateway");

    // Gateway configuration / property keys
    public static final String CONFIG_GATEWAY_ID = "gatewayId";
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

    // Channel ids - heatingCircuit group (lower-case-hyphen per openHAB naming convention)
    public static final String CHANNEL_HC_MANUAL_ROOM_SETPOINT = "manual-room-setpoint";

    // Channel ids - dhw group
    public static final String CHANNEL_DHW_CHARGE_DURATION = "charge-duration";
    public static final String CHANNEL_DHW_SINGLE_CHARGE_SETPOINT = "single-charge-setpoint";
    public static final String CHANNEL_DHW_OPERATION_MODE = "operation-mode";
    public static final String CHANNEL_DHW_CHARGE = "charge";
    public static final String CHANNEL_DHW_REDUCE_TEMP_ON_ALARM = "reduce-temp-on-alarm";

    // Channel ids - system group
    public static final String CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE = "outdoor-temperature";

    // Channel ids - heatSource group
    public static final String CHANNEL_HEAT_SOURCE_CH_STATUS = "ch-status";
    public static final String CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE = "actual-supply-temperature";
    public static final String CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE = "return-temperature";
    public static final String CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS = "number-of-starts";
    public static final String CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM = "working-time-total-system";

    // Channel ids - notifications group
    public static final String CHANNEL_NOTIFICATIONS_ACTIVE = "active";

    /**
     * PointT resource paths. The heating-circuit/DHW paths were confirmed against the
     * reverse-engineering analysis ({@code buderus-reverse.md}, section 1); the heat-source and
     * notifications paths, and the exact DHW operation mode option values (see
     * {@code dhw-operation-mode} in {@code thing-types.xml}), were confirmed against a real
     * capture from a tested K30/MX300/MX400 gateway (see the {@code buderus-main} project's
     * README "Status" section). Note that {@code heatSources} paths are flat
     * ({@code /heatSources/...}), unlike {@code heatingCircuits}/{@code dhwCircuits} which nest
     * under a sub-id ({@code hc1}/{@code dhw1}) - the tested gateway did not expose an
     * {@code hs1}-style sub-id for heat sources.
     */
    public static final class ResourcePaths {

        public static final String OUTDOOR_TEMPERATURE = "/system/sensors/temperatures/outdoor_t1";
        public static final String MANUAL_ROOM_SETPOINT = "/heatingCircuits/hc1/manualRoomSetpoint";
        public static final String DHW_CHARGE_DURATION = "/dhwCircuits/dhw1/chargeDuration";
        public static final String DHW_SINGLE_CHARGE_SETPOINT = "/dhwCircuits/dhw1/singleChargeSetpoint";
        public static final String DHW_OPERATION_MODE = "/dhwCircuits/dhw1/operationMode";
        public static final String DHW_CHARGE = "/dhwCircuits/dhw1/charge";
        public static final String DHW_REDUCE_TEMP_ON_ALARM = "/dhwCircuits/dhw1/reduceTempOnAlarm";
        public static final String GATEWAY_SERIAL_ID = "/gateway/serialId";
        public static final String GATEWAY_FIRMWARE_VERSION = "/gateway/versionFirmware";
        public static final String GATEWAY_HARDWARE_VERSION = "/gateway/versionHardware";
        public static final String HEAT_SOURCE_CH_STATUS = "/heatSources/chStatus";
        public static final String HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE = "/heatSources/actualSupplyTemperature";
        public static final String HEAT_SOURCE_RETURN_TEMPERATURE = "/heatSources/returnTemperature";
        public static final String HEAT_SOURCE_NUMBER_OF_STARTS = "/heatSources/numberOfStarts";
        public static final String HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM = "/heatSources/workingTime/totalSystem";
        public static final String NOTIFICATIONS = "/notifications";

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
