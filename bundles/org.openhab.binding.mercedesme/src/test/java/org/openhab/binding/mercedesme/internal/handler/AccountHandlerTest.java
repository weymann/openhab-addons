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
package org.openhab.binding.mercedesme.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.api.Websocket;
import org.openhab.binding.mercedesme.internal.api.WebsocketMock;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.discovery.MercedesMeDiscoveryService;
import org.openhab.binding.mercedesme.internal.utils.Utils;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.test.storage.VolatileStorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.internal.BridgeImpl;

import com.daimler.mbcarkit.proto.Client.ClientMessage;
import com.daimler.mbcarkit.proto.VehicleEvents.DoubleAttribute;
import com.daimler.mbcarkit.proto.VehicleEvents.PushMessage;
import com.daimler.mbcarkit.proto.VehicleEvents.VehicleStatusUpdate;
import com.daimler.mbcarkit.proto.VehicleEvents.VehicleStatusUpdates;

/**
 * {@link AccountHandlerTest} regression tests for the typed-push acknowledgment semantics: an update must only
 * be acknowledged once {@link AccountHandler#distributeVehicleUpdates(Map)} reports it was delivered.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AccountHandlerTest {

    /**
     * Counts {@link Websocket#websocketUpdate()} calls to observe refresh triggers without a real WebSocket.
     * {@link Websocket#authTokenIsValid()} is forced to {@code true}, otherwise a refresh falls back to a login
     * instead of requesting an update.
     */
    private static class CountingWebsocketMock extends WebsocketMock {
        final AtomicInteger updateCount = new AtomicInteger();

        CountingWebsocketMock(AccountHandler atrl, HttpClient hc, AccountConfiguration ac, LocaleProvider l) {
            super(atrl, hc, ac, l, new VolatileStorageService().getStorage(""));
        }

        @Override
        public void websocketUpdate() {
            updateCount.incrementAndGet();
        }

        @Override
        public boolean authTokenIsValid() {
            return true;
        }
    }

    /**
     * Counts {@link Websocket#sendAcknowledgeMessage(ClientMessage)} calls and counts down the shared latch,
     * which signals the "acknowledged" outcome (see {@link LatchedAccountHandler} for the other).
     */
    private static class TrackingWebsocketMock extends WebsocketMock {
        volatile int ackCount = 0;
        private final CountDownLatch processed;

        TrackingWebsocketMock(AccountHandler atrl, HttpClient hc, AccountConfiguration ac, LocaleProvider l,
                CountDownLatch processed) {
            super(atrl, hc, ac, l, new VolatileStorageService().getStorage(""));
            this.processed = processed;
        }

        @Override
        public void sendAcknowledgeMessage(ClientMessage message) {
            ackCount++;
            processed.countDown();
        }
    }

    /**
     * Signals when the async message-processing thread has finished deciding whether to distribute and
     * acknowledge, so tests need no arbitrary sleep. It counts down only on the "not distributed" outcome;
     * on success the acknowledgment from {@link TrackingWebsocketMock} is the last step.
     */
    private static class LatchedAccountHandler extends AccountHandler {
        final CountDownLatch processed = new CountDownLatch(1);

        LatchedAccountHandler(Bridge bridge, MercedesMeDiscoveryService mmds, HttpClient hc, LocaleProvider lp) {
            super(bridge, mmds, hc, lp, new VolatileStorageService());
        }

        @Override
        public boolean distributeVehicleUpdates(Map<String, VehicleStatusAttributes> map) {
            boolean result = super.distributeVehicleUpdates(map);
            if (!result) {
                processed.countDown();
            }
            return result;
        }

        @Override
        public void discovery(String vin) {
            // no-op in tests - the real implementation needs a live HTTP response
        }
    }

    private static PushMessage buildTypedPush(long sequenceNumber, String vin, boolean fullUpdate) {
        VehicleStatusUpdate vsu = VehicleStatusUpdate.newBuilder().setFullUpdate(fullUpdate).build();
        VehicleStatusUpdates vsus = VehicleStatusUpdates.newBuilder().setSequenceNumber(sequenceNumber)
                .putVehicleStatusUpdates(vin, vsu).build();
        return PushMessage.newBuilder().setVehicleStatusUpdates(vsus).build();
    }

    @Test
    void typedPushIsNotAcknowledgedWhenNoActiveHandlerExistsForVin() throws InterruptedException {
        LatchedAccountHandler ah = new LatchedAccountHandler(mock(Bridge.class), mock(MercedesMeDiscoveryService.class),
                mock(HttpClient.class), mock(LocaleProvider.class));
        TrackingWebsocketMock ws = new TrackingWebsocketMock(ah, mock(HttpClient.class), ah.config,
                mock(LocaleProvider.class), ah.processed);
        ah.api = ws;

        // no registerVin() call - activeVehicleHandlerMap stays empty, so no acknowledgment
        ah.enqueueMessage(buildTypedPush(1L, "WDB1230011ANONYMIZ", false));

        assertTrue(ah.processed.await(2, TimeUnit.SECONDS), "message was not processed in time");
        assertEquals(0, ws.ackCount,
                "a VehicleStatusUpdates for a VIN with no active handler must not be acknowledged - "
                        + "the server never resends an acknowledged sequence, so this would silently lose the update");
    }

    @Test
    void typedPushIsAcknowledgedWhenDeliveredToAnActiveHandler() throws InterruptedException {
        LatchedAccountHandler ah = new LatchedAccountHandler(mock(Bridge.class), mock(MercedesMeDiscoveryService.class),
                mock(HttpClient.class), mock(LocaleProvider.class));
        TrackingWebsocketMock ws = new TrackingWebsocketMock(ah, mock(HttpClient.class), ah.config,
                mock(LocaleProvider.class), ah.processed);
        ah.api = ws;

        String vin = "WDB1230011ANONYMIZ";
        Thing thingMock = mock(Thing.class);
        when(thingMock.getThingTypeUID()).thenReturn(Constants.THING_TYPE_BEV);
        when(thingMock.getProperties()).thenReturn(new HashMap<>());
        VehicleHandler handlerMock = mock(VehicleHandler.class);
        when(handlerMock.getThing()).thenReturn(thingMock);
        ah.registerVin(vin, handlerMock);

        ah.enqueueMessage(buildTypedPush(2L, vin, true));

        assertTrue(ah.processed.await(2, TimeUnit.SECONDS), "message was not processed in time");
        verify(handlerMock, times(1)).enqueueUpdate(any());
        assertEquals(1, ws.ackCount, "an update successfully delivered to an active handler must be acknowledged");
    }

    @Test
    void forceUpdateRefreshesImmediatelyAndRestartsTheRefreshInterval() throws InterruptedException {
        Utils.initialize(Utils.timeZoneProvider, Utils.localeProvider);
        Map<String, Object> config = new HashMap<>();
        config.put("email", "test@junit.org");
        config.put("password", "junitPassword");
        config.put("region", "row");
        config.put("refreshInterval", 15);
        BridgeImpl bridge = new BridgeImpl(new ThingTypeUID("test", "account"), "MB");
        bridge.setConfiguration(new Configuration(config));
        AccountHandler ah = new AccountHandler(bridge, mock(MercedesMeDiscoveryService.class), mock(HttpClient.class),
                mock(LocaleProvider.class), new VolatileStorageService());
        ah.setCallback(new ThingCallbackListener());
        try {
            // initialize schedules the first regular refresh two seconds ahead
            ah.initialize();
            CountingWebsocketMock ws = new CountingWebsocketMock(ah, mock(HttpClient.class), ah.config,
                    mock(LocaleProvider.class));
            ah.api = ws;

            ah.forceUpdate("WDB1230011ANONYMIZ");
            assertTrue(ws.updateCount.get() >= 1, "forceUpdate must trigger an immediate update");

            // the refresh pending before the force update must be moved to the refresh interval - within the next
            // seconds only the refresh already triggered by the force update may be observed
            ws.updateCount.set(0);
            Instant deadline = Instant.now().plusSeconds(3);
            while (ws.updateCount.get() == 0 && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertEquals(0, ws.updateCount.get(),
                    "the refresh scheduled before the force update must be moved to the refresh interval");
        } finally {
            ah.dispose();
        }
    }

    @Test
    void traceOutputMasksVinAndGpsPosition() {
        VehicleStatusUpdate update = VehicleStatusUpdate.newBuilder().setFinOrVin("W1N9N0CB6SJ140713")
                .setPositionLat(DoubleAttribute.newBuilder().setValue(52.520008).build())
                .setPositionLong(DoubleAttribute.newBuilder().setValue(13.404954).build()).build();

        VehicleStatusUpdate anonymized = AccountHandler.anonymizeForTrace(update);

        assertEquals("*************0713", anonymized.getFinOrVin(),
                "only the last 4 characters of the VIN may be logged");
        assertEquals(1.23, anonymized.getPositionLat().getValue(), 0.0, "latitude replaced by placeholder");
        assertEquals(4.56, anonymized.getPositionLong().getValue(), 0.0, "longitude replaced by placeholder");
    }
}
