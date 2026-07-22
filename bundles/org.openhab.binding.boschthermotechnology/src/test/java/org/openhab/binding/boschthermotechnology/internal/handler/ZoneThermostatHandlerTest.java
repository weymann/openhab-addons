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
package org.openhab.binding.boschthermotechnology.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_ZONE_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_GATEWAY;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ZONE_THERMOSTAT;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.openhab.binding.boschthermotechnology.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.BridgeBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;

import com.google.gson.JsonPrimitive;

/**
 * {@link ZoneThermostatHandler} needs its own test rather than
 * {@link AbstractSimpleChildHandlerTest} because it additionally requires a {@code zoneId}
 * configuration value that the other five simple child handlers do not have.
 *
 * @author Bernd Weymann - Initial contribution
 */
class ZoneThermostatHandlerTest {

    private static final ThingUID ACCOUNT_UID = new ThingUID(THING_TYPE_ACCOUNT, "acc1");
    private static final ThingUID GATEWAY_UID = new ThingUID(THING_TYPE_GATEWAY, ACCOUNT_UID, "gw1");
    private static final ThingUID ZONE_UID = new ThingUID(THING_TYPE_ZONE_THERMOSTAT, GATEWAY_UID, "zone1");

    private GatewayHandler gatewayHandler = mock(GatewayHandler.class);
    private PointTApiClient apiClient = mock(PointTApiClient.class);
    private CallbackMock callback = new CallbackMock();
    private ZoneThermostatHandler handler = createHandler(Map.of(CONFIG_GATEWAY_ID, "gw1", CONFIG_ZONE_ID, "zone1"));

    private ZoneThermostatHandler createHandler(Map<String, Object> config) {
        gatewayHandler = mock(GatewayHandler.class);
        apiClient = mock(PointTApiClient.class);
        callback = new CallbackMock();

        Bridge gatewayBridge = BridgeBuilder.create(THING_TYPE_GATEWAY, GATEWAY_UID).build();
        gatewayBridge.setHandler(gatewayHandler);

        Thing zoneThing = ThingBuilder.create(THING_TYPE_ZONE_THERMOSTAT, ZONE_UID).withBridge(GATEWAY_UID)
                .withConfiguration(new Configuration(config)).build();

        callback.setBridge(gatewayBridge);
        ZoneThermostatHandler zoneHandler = new ZoneThermostatHandler(zoneThing);
        zoneHandler.setCallback(callback);
        return zoneHandler;
    }

    @AfterEach
    void tearDown() {
        handler.dispose();
    }

    @Test
    void whenZoneIdAndGatewayIdAreSet_thenThingGoesOnline() throws Exception {
        // Arrange
        handler = createHandler(Map.of(CONFIG_GATEWAY_ID, "gw1", CONFIG_ZONE_ID, "zone1"));
        when(gatewayHandler.getApiClient()).thenReturn(apiClient);
        when(gatewayHandler.getValidAccessToken()).thenReturn("token");
        when(apiClient.getResource(any(), any(), any()))
                .thenReturn(new ResourceDto("x", "Float", 0, new JsonPrimitive(21.0)));

        // Act
        handler.initialize();

        // Assert
        callback.waitForOnline();
    }

    @Test
    void whenZoneIdIsMissing_thenThingGoesOfflineWithConfigurationError() {
        // Arrange
        handler = createHandler(Map.of(CONFIG_GATEWAY_ID, "gw1"));

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertEquals(ThingStatusDetail.CONFIGURATION_ERROR, callback.getStatus().getStatusDetail());
    }
}
