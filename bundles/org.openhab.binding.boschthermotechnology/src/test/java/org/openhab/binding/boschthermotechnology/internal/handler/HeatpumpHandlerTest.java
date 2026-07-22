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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEATING_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_GATEWAY;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_HEATPUMP;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.BridgeBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.types.State;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Unit tests for {@link HeatpumpHandler}: the happy path (circuits discovered, thing goes ONLINE),
 * the error path ($QA fix: both circuit lists empty must not wipe existing channels and must go
 * OFFLINE), and the DHW operation mode Number-code <-> API-String mapping table.
 *
 * @author Bernd Weymann - Initial contribution
 */
class HeatpumpHandlerTest {

    private static final ThingUID ACCOUNT_UID = new ThingUID(THING_TYPE_ACCOUNT, "acc1");
    private static final ThingUID GATEWAY_UID = new ThingUID(THING_TYPE_GATEWAY, ACCOUNT_UID, "gw1");
    private static final ThingUID HEATPUMP_UID = new ThingUID(THING_TYPE_HEATPUMP, GATEWAY_UID, "heatpump");

    private GatewayHandler gatewayHandler = mock(GatewayHandler.class);
    private PointTApiClient apiClient = mock(PointTApiClient.class);
    private CallbackMock callback = new CallbackMock();
    private HeatpumpHandler handler = createHandler();

    private HeatpumpHandler createHandler() {
        gatewayHandler = mock(GatewayHandler.class);
        apiClient = mock(PointTApiClient.class);
        callback = new CallbackMock();

        Bridge gatewayBridge = BridgeBuilder.create(THING_TYPE_GATEWAY, GATEWAY_UID).build();
        gatewayBridge.setHandler(gatewayHandler);

        Thing heatpumpThing = ThingBuilder.create(THING_TYPE_HEATPUMP, HEATPUMP_UID).withBridge(GATEWAY_UID)
                .withConfiguration(new Configuration(Map.of(CONFIG_GATEWAY_ID, "gw1"))).build();

        callback.setBridge(gatewayBridge);
        HeatpumpHandler heatpumpHandler = new HeatpumpHandler(heatpumpThing);
        heatpumpHandler.setCallback(callback);
        return heatpumpHandler;
    }

    @BeforeEach
    void setUp() throws Exception {
        handler = createHandler();
        when(gatewayHandler.getApiClient()).thenReturn(apiClient);
        when(gatewayHandler.getValidAccessToken()).thenReturn("token");
    }

    @AfterEach
    void tearDown() {
        handler.dispose();
    }

    @Test
    void whenCircuitsAreDiscovered_thenThingGoesOnlineWithDynamicChannels() throws Exception {
        // Arrange
        when(apiClient.listResourceIds(any(), any(), eq(HEATING_CIRCUITS_LIST))).thenReturn(List.of("hc1"));
        when(apiClient.listResourceIds(any(), any(), eq(DHW_CIRCUITS_LIST))).thenReturn(List.of("dhw1"));
        when(apiClient.getResource(any(), any(), any()))
                .thenReturn(new org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto("x", "Float", 0,
                        new JsonPrimitive(21.0)));

        // Act
        handler.initialize();

        // Assert
        callback.waitForOnline();
        assertTrue(handler.getThing().getChannel("heating-circuit-hc1#manual-room-setpoint") != null,
                "Channel group must be named after the circuit's own id, not its list position - see $QA comment");
    }

    @Test
    void whenNoCircuitsAreReported_thenThingGoesOfflineWithoutWipingChannels() throws Exception {
        // Arrange
        when(apiClient.listResourceIds(any(), any(), eq(HEATING_CIRCUITS_LIST))).thenReturn(List.of());
        when(apiClient.listResourceIds(any(), any(), eq(DHW_CIRCUITS_LIST))).thenReturn(List.of());

        // Act
        handler.initialize();

        // Assert - $QA fix: both lists empty must be treated as a discovery failure (OFFLINE), and
        // must return before calling updateThing(), so a transient API hiccup can never wipe an
        // already-working thing's channels.
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @ParameterizedTest
    @CsvSource({ "0, Off", "1, Eco+", "2, Eco", "3, Comfort", "4, Auto" })
    void dhwOperationModeCodeMapsToApiValueAndBack(int code, String apiValue) {
        // Act
        JsonElement written = handler.toDhwOperationModeJson(new DecimalType(code));
        State read = handler.toDhwOperationModeState(new JsonPrimitive(apiValue));

        // Assert
        assertEquals(apiValue, written.getAsString());
        assertEquals(new DecimalType(code), read);
    }

    @Test
    void dhwOperationModeUnknownApiValueMapsToUndef() {
        State read = handler.toDhwOperationModeState(new JsonPrimitive("SomeFutureMode"));
        assertEquals(org.openhab.core.types.UnDefType.UNDEF, read);
    }
}
