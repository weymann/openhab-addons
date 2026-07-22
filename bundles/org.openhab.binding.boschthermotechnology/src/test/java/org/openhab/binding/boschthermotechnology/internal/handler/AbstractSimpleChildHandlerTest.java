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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_GATEWAY;

import java.util.Map;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.openhab.binding.boschthermotechnology.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.BridgeBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;

import com.google.gson.JsonPrimitive;

/**
 * Shared happy-path / error-path test skeleton for the six simple child handlers that all follow
 * the exact same {@code initialize()} shape (validate {@code gatewayId} -> resolve
 * {@code GatewayHandler} bridge -> build a fixed {@link java.util.List} of
 * {@link org.openhab.binding.boschthermotechnology.internal.handler.support.ChannelResourceBinding}
 * -> start {@link org.openhab.binding.boschthermotechnology.internal.handler.support.ResourcePollingSupport}):
 * {@link PvHandler}, {@link PoolHandler}, {@link VentilationZoneHandler},
 * {@link ZoneThermostatHandler}, {@link EnergyMonitoringHandler}, {@link AcUnitHandler}.
 *
 * <p>
 * Factored out per {@code rules/testing-rules.md}'s guidance to avoid copy-pasted near-identical
 * test methods - each handler still gets its own concrete test class (satisfying the "every new
 * *Handler class needs a happy-path and an error-path test" minimum coverage rule) but the
 * boilerplate wiring (bridge/thing construction, callback, mocked API client) lives here once.
 * {@link HeatpumpHandler} and {@link GatewayHandler} have their own dedicated, non-shared test
 * classes because their {@code initialize()} logic is genuinely different (dynamic channel
 * discovery, resp. the bridge-only system channels and child-discovery trigger).
 * {@link WaterSoftenerHandler} also has its own dedicated test since it never polls at all.
 *
 * @author Bernd Weymann - Initial contribution
 */
abstract class AbstractSimpleChildHandlerTest {

    private static final ThingUID ACCOUNT_UID = new ThingUID(THING_TYPE_ACCOUNT, "acc1");
    private static final ThingUID GATEWAY_UID = new ThingUID(THING_TYPE_GATEWAY, ACCOUNT_UID, "gw1");

    private GatewayHandler gatewayHandler = mock(GatewayHandler.class);
    private PointTApiClient apiClient = mock(PointTApiClient.class);
    private CallbackMock callback = new CallbackMock();
    private BaseThingHandler handler = createFreshHandler(true);

    /** @return the thing-type this concrete test targets, e.g. {@code THING_TYPE_POOL}. */
    protected abstract ThingTypeUID thingType();

    /** @return a new handler instance wrapping the given thing. */
    protected abstract BaseThingHandler createHandler(Thing thing);

    private BaseThingHandler createFreshHandler(boolean withBridge) {
        gatewayHandler = mock(GatewayHandler.class);
        apiClient = mock(PointTApiClient.class);
        callback = new CallbackMock();

        ThingUID childUid = new ThingUID(thingType(), GATEWAY_UID, "child");
        var thingBuilder = ThingBuilder.create(thingType(), childUid)
                .withConfiguration(new Configuration(Map.of(CONFIG_GATEWAY_ID, "gw1")));

        if (withBridge) {
            Bridge gatewayBridge = BridgeBuilder.create(THING_TYPE_GATEWAY, GATEWAY_UID).build();
            gatewayBridge.setHandler(gatewayHandler);
            callback.setBridge(gatewayBridge);
            thingBuilder = thingBuilder.withBridge(GATEWAY_UID);
        }

        Thing thing = thingBuilder.build();
        BaseThingHandler newHandler = createHandler(thing);
        newHandler.setCallback(callback);
        return newHandler;
    }

    @AfterEach
    void tearDown() {
        handler.dispose();
    }

    @Test
    void whenAllBoundResourcesResolve_thenThingGoesOnline() throws Exception {
        // Arrange
        handler = createFreshHandler(true);
        when(gatewayHandler.getApiClient()).thenReturn(apiClient);
        when(gatewayHandler.getValidAccessToken()).thenReturn("token");
        when(apiClient.getResource(any(), any(), any()))
                .thenReturn(new ResourceDto("x", "Float", 0, new JsonPrimitive(1)));

        // Act
        handler.initialize();

        // Assert
        callback.waitForOnline();
    }

    @Test
    void whenApiCallFails_thenThingGoesOfflineWithCommunicationError() throws Exception {
        // Arrange
        handler = createFreshHandler(true);
        when(gatewayHandler.getApiClient()).thenReturn(apiClient);
        when(gatewayHandler.getValidAccessToken()).thenReturn("token");
        when(apiClient.getResource(any(), any(), any()))
                .thenThrow(new PointTApiException("boom", HttpStatus.INTERNAL_SERVER_ERROR_500));

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenBridgeIsNotYetAvailable_thenThingGoesOfflineWithBridgeUninitialized() {
        // Arrange
        handler = createFreshHandler(false);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }
}
