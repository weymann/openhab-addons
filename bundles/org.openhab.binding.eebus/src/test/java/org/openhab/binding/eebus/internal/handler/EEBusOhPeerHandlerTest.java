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
package org.openhab.binding.eebus.internal.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_PEER;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_SERVICE;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.eebus.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.BridgeBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.binding.builder.ThingStatusInfoBuilder;

/**
 * Unit tests for {@link EEBusOhPeerHandler}, covering the scenarios from
 * {@code docs/changes/decouple-oh-peer-config-from-pairing/specs/thing-model/spec.md}: a
 * configured-but-not-yet-paired Thing stays {@code OFFLINE}
 * ({@code ThingStatusDetail.CONFIGURATION_PENDING}), only {@link EEBusOhPeerHandler#pair()}
 * brings it {@code ONLINE}, and {@link EEBusOhPeerHandler#unpair()} reverts that without
 * removing the Thing - superseding the older "creating this Thing performs pairing" behavior
 * from {@code docs/changes/separate-real-and-oh-peer-things/specs/thing-model/spec.md}, revised
 * by docs/ADR/012-pairing-trust-property-and-actions.md. The trusted-SKI-set assertions
 * themselves still live with {@code EEBusHandler}, not exercised here since this handler only
 * mirrors Bridge status plus its own pairing property, per its class javadoc.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class EEBusOhPeerHandlerTest {

    private static final ThingUID BRIDGE_UID = new ThingUID(THING_TYPE_SERVICE, "ems1");
    private static final ThingUID OH_PEER_UID = new ThingUID(THING_TYPE_OH_PEER, BRIDGE_UID, "peer1");
    private static final String SKI = "dd8ba427c0a449180513fbb51bf6f0bbdbf0c821";

    private CallbackMock callback = new CallbackMock();

    @BeforeEach
    void setUp() {
        callback = new CallbackMock();
    }

    /**
     * @return a not-yet-initialized {@link EEBusOhPeerHandler} for a Thing with a valid
     *         {@value #SKI}, whose parent Bridge is already {@code ONLINE} - the common Arrange
     *         step shared by most tests below.
     */
    private EEBusOhPeerHandler createHandlerWithOnlineBridgeAndConfiguredSki() {
        Bridge bridge = BridgeBuilder.create(THING_TYPE_SERVICE, BRIDGE_UID).build();
        bridge.setStatusInfo(ThingStatusInfoBuilder.create(ThingStatus.ONLINE).build());
        callback.setBridge(bridge);

        Configuration config = new Configuration();
        config.put("ski", SKI);
        Thing ohPeerThing = ThingBuilder.create(THING_TYPE_OH_PEER, OH_PEER_UID).withBridge(BRIDGE_UID)
                .withConfiguration(config).build();
        EEBusOhPeerHandler handler = new EEBusOhPeerHandler(ohPeerThing);
        handler.setCallback(callback);
        return handler;
    }

    @Test
    void whenSkiConfiguredAndBridgeOnlineButNotPairedThenStatusIsOffline() {
        // Arrange
        EEBusOhPeerHandler handler = createHandlerWithOnlineBridgeAndConfiguredSki();

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertFalse(handler.isPaired());
    }

    @Test
    void whenPairedAndBridgeOnlineThenStatusIsOnline() {
        // Arrange
        EEBusOhPeerHandler handler = createHandlerWithOnlineBridgeAndConfiguredSki();
        handler.initialize();
        callback.waitForStatus(ThingStatus.OFFLINE);

        // Act
        handler.pair();

        // Assert
        callback.waitForOnline();
        assertTrue(handler.isPaired());
    }

    @Test
    void whenUnpairedAfterPairingThenStatusIsOfflineAgain() {
        // Arrange
        EEBusOhPeerHandler handler = createHandlerWithOnlineBridgeAndConfiguredSki();
        handler.initialize();
        handler.pair();
        callback.waitForOnline();

        // Act
        handler.unpair();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertFalse(handler.isPaired());
    }

    @Test
    void whenPairInvokedOnAlreadyPairedThingThenRemainsPairedWithoutError() {
        // Arrange
        EEBusOhPeerHandler handler = createHandlerWithOnlineBridgeAndConfiguredSki();
        handler.initialize();
        handler.pair();
        callback.waitForOnline();

        // Act
        handler.pair();

        // Assert
        callback.waitForOnline();
        assertTrue(handler.isPaired());
    }

    @Test
    void whenUnpairInvokedOnAlreadyUnpairedThingThenRemainsUnpairedWithoutError() {
        // Arrange
        EEBusOhPeerHandler handler = createHandlerWithOnlineBridgeAndConfiguredSki();
        handler.initialize();
        callback.waitForStatus(ThingStatus.OFFLINE);

        // Act
        handler.unpair();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertFalse(handler.isPaired());
    }

    @Test
    void whenSkiBlankThenStatusIsOffline() {
        // Arrange
        Thing ohPeerThing = ThingBuilder.create(THING_TYPE_OH_PEER, OH_PEER_UID).withBridge(BRIDGE_UID)
                .withConfiguration(new Configuration()).build();
        EEBusOhPeerHandler handler = new EEBusOhPeerHandler(ohPeerThing);
        handler.setCallback(callback);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenBridgeNotOnlineThenStatusIsOffline() {
        // Arrange
        Bridge bridge = BridgeBuilder.create(THING_TYPE_SERVICE, BRIDGE_UID).build();
        bridge.setStatusInfo(ThingStatusInfoBuilder.create(ThingStatus.OFFLINE).build());
        callback.setBridge(bridge);

        Configuration config = new Configuration();
        config.put("ski", SKI);
        Thing ohPeerThing = ThingBuilder.create(THING_TYPE_OH_PEER, OH_PEER_UID).withBridge(BRIDGE_UID)
                .withConfiguration(config).build();
        EEBusOhPeerHandler handler = new EEBusOhPeerHandler(ohPeerThing);
        handler.setCallback(callback);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }
}
