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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_GATEWAY;

import java.util.Map;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.openhab.binding.boschthermotechnology.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.BridgeBuilder;

import com.google.gson.JsonPrimitive;

/**
 * Unit tests for {@link GatewayHandler}: the happy path (poll succeeds, thing goes ONLINE), the
 * error path (poll fails, thing goes OFFLINE), and specifically the {@code updateStatus} override
 * that triggers child-thing discovery - this is a self-caught fix from the ADR-006 implementation
 * (see the class Javadoc) and is exactly the kind of regression a $QA test should pin down: it must
 * fire once on the OFFLINE-to-ONLINE transition, and must not re-fire on every subsequent
 * already-ONLINE poll cycle.
 *
 * @author Bernd Weymann - Initial contribution
 */
class GatewayHandlerTest {

    private static final ThingUID ACCOUNT_UID = new ThingUID(THING_TYPE_ACCOUNT, "acc1");
    private static final ThingUID GATEWAY_UID = new ThingUID(THING_TYPE_GATEWAY, ACCOUNT_UID, "gw1");

    private AccountBridgeHandler accountHandler = mock(AccountBridgeHandler.class);
    private PointTApiClient apiClient = mock(PointTApiClient.class);
    private CallbackMock callback = new CallbackMock();
    private GatewayHandler handler = createHandler();

    private GatewayHandler createHandler() {
        accountHandler = mock(AccountBridgeHandler.class);
        apiClient = mock(PointTApiClient.class);
        callback = new CallbackMock();

        Bridge accountBridge = BridgeBuilder.create(THING_TYPE_ACCOUNT, ACCOUNT_UID).build();
        accountBridge.setHandler(accountHandler);

        Bridge gatewayBridge = BridgeBuilder.create(THING_TYPE_GATEWAY, GATEWAY_UID).withBridge(ACCOUNT_UID)
                .withConfiguration(new Configuration(Map.of(CONFIG_GATEWAY_ID, "gw1"))).build();

        callback.setBridge(accountBridge);
        GatewayHandler gatewayHandler = new GatewayHandler(gatewayBridge);
        gatewayHandler.setCallback(callback);
        return gatewayHandler;
    }

    @BeforeEach
    void setUp() throws Exception {
        handler = createHandler();
        when(accountHandler.getApiClient()).thenReturn(apiClient);
        when(accountHandler.getValidAccessToken()).thenReturn("token");
    }

    @AfterEach
    void tearDown() {
        handler.dispose();
    }

    @Test
    void whenAllSystemResourcesResolve_thenThingGoesOnline() throws Exception {
        // Arrange
        when(apiClient.getResource(any(), any(), any()))
                .thenReturn(new ResourceDto("x", "Float", 0, new JsonPrimitive(12.3)));

        // Act
        handler.initialize();

        // Assert
        callback.waitForOnline();
    }

    @Test
    void whenApiCallFails_thenThingGoesOfflineWithCommunicationError() throws Exception {
        // Arrange
        when(apiClient.getResource(any(), any(), any()))
                .thenThrow(new PointTApiException("boom", HttpStatus.INTERNAL_SERVER_ERROR_500));

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenGatewayFirstReachesOnline_thenChildDiscoveryScanIsTriggeredExactlyOnce() throws Exception {
        // Arrange - call the protected updateStatus(...) override directly (this test class is in
        // the same package, so this is a normal protected-access call, not reflection) to drive the
        // OFFLINE-to-ONLINE transition deterministically, without depending on the async poll timing
        // that handler.initialize() would otherwise introduce.
        java.util.concurrent.atomic.AtomicInteger scanCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch firstScan = new java.util.concurrent.CountDownLatch(1);
        handler.setChildDiscoveryScanTrigger(() -> {
            scanCount.incrementAndGet();
            firstScan.countDown();
        });

        // Act - first transition into ONLINE must trigger exactly one scan.
        handler.updateStatus(ThingStatus.ONLINE, org.openhab.core.thing.ThingStatusDetail.NONE, null);
        if (!firstScan.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            fail("Child discovery scan was not triggered on the ONLINE transition");
        }
        assertEquals(1, scanCount.get());

        // Act - a second updateStatus(ONLINE, ...) call while already ONLINE (mirrors what a
        // successful poll cycle does on every iteration) must NOT re-trigger discovery - this is
        // the exact regression covered by the self-caught bug documented on the class Javadoc.
        handler.updateStatus(ThingStatus.ONLINE, org.openhab.core.thing.ThingStatusDetail.NONE, null);
        awaitBriefly();

        // Assert
        assertEquals(1, scanCount.get());
    }

    /**
     * Bounded wait used only to give the scheduler a chance to run a task that must NOT happen -
     * there is no positive event to poll for in a negative assertion, so a short, generous timeout
     * is the accepted trade-off (mirrors what {@code Awaitility}'s {@code pollDelay}/{@code during}
     * do internally; this binding does not currently have Awaitility as a test dependency).
     */
    private void awaitBriefly() throws InterruptedException {
        Thread.sleep(300);
    }
}
