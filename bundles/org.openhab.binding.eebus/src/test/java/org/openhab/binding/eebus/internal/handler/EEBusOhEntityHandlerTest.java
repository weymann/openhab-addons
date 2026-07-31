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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_DEVICE;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_ENTITY;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * Unit tests for {@link EEBusOhEntityHandler}, covering the scenarios from
 * {@code docs/changes/oh-device-oh-entity-rename/specs/thing-model-rename/spec.md}: an
 * {@code eebus:oh-entity} Thing's status now follows the parent Bridge's trust decision
 * ({@code EEBusHandler#isTrusted(String)}) rather than an own {@code paired} property, since
 * docs/ADR/024-oh-device-oh-entity-rename.md moved trust granting to the Bridge and removed
 * this handler's {@code pair()}/{@code unpair()} Thing Actions entirely - superseding the older
 * {@code docs/changes/decouple-oh-peer-config-from-pairing/specs/thing-model/spec.md} behavior
 * this test class used to cover. The parent Bridge itself is a Mockito mock exposing a mocked
 * {@link EEBusHandler} via {@link Bridge#getHandler()}, since constructing a real
 * {@link EEBusHandler} needs several collaborators (mDNS client, port pool, storage, ...) this
 * handler-level test has no business wiring up; only {@link Bridge#getStatus()}/
 * {@link Bridge#getHandler()} and {@link EEBusHandler#isTrusted(String)} are ever read by
 * {@link EEBusOhEntityHandler#applyStatus()}.
 *
 * <p>
 * {@code @SuppressWarnings("null")}: Mockito is not designed with null type annotations in
 * mind, so combining it with this {@code @NonNullByDefault} test class produces "unsafe
 * interpretation" compiler advisories with no null-safety benefit.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
class EEBusOhEntityHandlerTest {

    private static final ThingUID BRIDGE_UID = new ThingUID(THING_TYPE_OH_DEVICE, "ems1");
    private static final ThingUID OH_ENTITY_UID = new ThingUID(THING_TYPE_OH_ENTITY, BRIDGE_UID, "entity1");
    private static final String SKI = "dd8ba427c0a449180513fbb51bf6f0bbdbf0c821";

    private CallbackMock callback = new CallbackMock();
    private EEBusHandler bridgeHandler = mock(EEBusHandler.class);

    @BeforeEach
    void setUp() {
        callback = new CallbackMock();
        bridgeHandler = mock(EEBusHandler.class);
    }

    /**
     * @param trusted the value {@link EEBusHandler#isTrusted(String)} should report for
     *            {@value #SKI} on the mocked parent Bridge
     * @return a not-yet-initialized {@link EEBusOhEntityHandler} for a Thing with a valid
     *         {@value #SKI}, whose parent Bridge is already {@code ONLINE} - the common Arrange
     *         step shared by most tests below.
     */
    private EEBusOhEntityHandler createHandlerWithOnlineBridge(boolean trusted) {
        Bridge bridge = mock(Bridge.class);
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(bridge.getHandler()).thenReturn(bridgeHandler);
        when(bridgeHandler.isTrusted(SKI)).thenReturn(trusted);
        callback.setBridge(bridge);

        Configuration config = new Configuration();
        config.put("ski", SKI);
        Thing ohEntityThing = ThingBuilder.create(THING_TYPE_OH_ENTITY, OH_ENTITY_UID).withBridge(BRIDGE_UID)
                .withConfiguration(config).build();
        EEBusOhEntityHandler handler = new EEBusOhEntityHandler(ohEntityThing);
        handler.setCallback(callback);
        return handler;
    }

    @Test
    void whenSkiConfiguredAndBridgeOnlineButNotTrustedThenStatusIsOffline() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(false);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenSkiTrustedAndBridgeOnlineThenStatusIsOnline() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);

        // Act
        handler.initialize();

        // Assert
        callback.waitForOnline();
    }

    @Test
    void whenTrustRevokedThenStatusIsOfflineAgain() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        when(bridgeHandler.isTrusted(SKI)).thenReturn(false);

        // Act - simulates the Bridge's uniform full-rebuild path (ADR-027) re-checking this
        // child's status after a trustedSkis config change, see
        // EEBusOhEntityHandler#applyStatus() javadoc
        handler.applyStatus();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenTrustGrantedAfterInitiallyNotTrustedThenStatusBecomesOnline() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(false);
        handler.initialize();
        callback.waitForStatus(ThingStatus.OFFLINE);
        when(bridgeHandler.isTrusted(SKI)).thenReturn(true);

        // Act - simulates the Bridge's uniform full-rebuild path (ADR-027) re-checking this
        // child's status after a trustedSkis config change
        handler.applyStatus();

        // Assert
        callback.waitForOnline();
    }

    @Test
    void whenSkiBlankThenStatusIsOffline() {
        // Arrange
        Thing ohEntityThing = ThingBuilder.create(THING_TYPE_OH_ENTITY, OH_ENTITY_UID).withBridge(BRIDGE_UID)
                .withConfiguration(new Configuration()).build();
        EEBusOhEntityHandler handler = new EEBusOhEntityHandler(ohEntityThing);
        handler.setCallback(callback);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    @Test
    void whenBridgeNotOnlineThenStatusIsOffline() {
        // Arrange
        Bridge bridge = mock(Bridge.class);
        when(bridge.getStatus()).thenReturn(ThingStatus.OFFLINE);
        callback.setBridge(bridge);

        Configuration config = new Configuration();
        config.put("ski", SKI);
        Thing ohEntityThing = ThingBuilder.create(THING_TYPE_OH_ENTITY, OH_ENTITY_UID).withBridge(BRIDGE_UID)
                .withConfiguration(config).build();
        EEBusOhEntityHandler handler = new EEBusOhEntityHandler(ohEntityThing);
        handler.setCallback(callback);

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
    }

    /**
     * Covers {@code docs/changes/dynamic-client-role-channels/specs/dynamic-channels/spec.md},
     * Scenario "Channel appears after first successful measurement resolution" - the
     * {@link EEBusOhEntityHandler#applyMpcPower} half of it (the detection/subscription half is
     * {@code EEBusMpcClientUseCase}'s responsibility, not exercised here per this test class's
     * own javadoc).
     */
    @Test
    void whenApplyMpcPowerCalledThenChannelCreatedAndStateUpdated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MPC,
                EEBusBindingConstants.CHANNEL_MPC_POWER);

        // Act
        handler.applyMpcPower(1234.5);

        // Assert
        Channel channel = handler.getThing().getChannel(channelUID);
        assertNotNull(channel);
        State state = callback.getState(channelUID.getAsString());
        assertEquals(new QuantityType<>(1234.5, Units.WATT), state);
    }

    /**
     * Covers {@code docs/changes/dynamic-client-role-channels/tasks.md} §6.1: repeated calls must
     * not duplicate the Channel - {@link EEBusOhEntityHandler#applyMpcPower} is called once per
     * measurement notification, not once ever.
     */
    @Test
    void whenApplyMpcPowerCalledTwiceThenChannelIsNotDuplicated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();

        // Act
        handler.applyMpcPower(100.0);
        handler.applyMpcPower(200.0);

        // Assert
        assertEquals(1, handler.getThing().getChannels().size());
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MPC,
                EEBusBindingConstants.CHANNEL_MPC_POWER);
        assertEquals(new QuantityType<>(200.0, Units.WATT), callback.getState(channelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/mpc-additional-datapoints/specs/mpc-additional-datapoints/spec.md}
     * - the {@link EEBusOhEntityHandler#applyMpcMeasurement} half of it (the resolution/dispatch
     * half is {@code EEBusMpcClientUseCase}'s responsibility, not exercised here, same split as
     * the {@code applyMpcPower} tests above). {@code current-phase-a} is used as the
     * representative one of the twelve additional data points - the method is otherwise identical
     * for all of them.
     */
    @Test
    void whenApplyMpcMeasurementCalledThenChannelCreatedAndStateUpdated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MPC,
                EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A);

        // Act
        handler.applyMpcMeasurement(EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A,
                EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_A, "Number:ElectricCurrent",
                "Current (Phase A)", 12.3, Units.AMPERE);

        // Assert
        Channel channel = handler.getThing().getChannel(channelUID);
        assertNotNull(channel);
        State state = callback.getState(channelUID.getAsString());
        assertEquals(new QuantityType<>(12.3, Units.AMPERE), state);
    }

    /**
     * Covers {@code docs/changes/mpc-additional-datapoints/tasks.md} §5.2: repeated calls for the
     * same Channel id must not duplicate the Channel, mirroring
     * {@link #whenApplyMpcPowerCalledTwiceThenChannelIsNotDuplicated}.
     */
    @Test
    void whenApplyMpcMeasurementCalledTwiceThenChannelIsNotDuplicated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();

        // Act
        handler.applyMpcMeasurement(EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A,
                EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_A, "Number:ElectricCurrent",
                "Current (Phase A)", 5.0, Units.AMPERE);
        handler.applyMpcMeasurement(EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A,
                EEBusBindingConstants.CHANNEL_TYPE_UID_MPC_CURRENT_PHASE_A, "Number:ElectricCurrent",
                "Current (Phase A)", 7.5, Units.AMPERE);

        // Assert
        assertEquals(1, handler.getThing().getChannels().size());
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MPC,
                EEBusBindingConstants.CHANNEL_MPC_CURRENT_PHASE_A);
        assertEquals(new QuantityType<>(7.5, Units.AMPERE), callback.getState(channelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/mgcp-client-usecase/tasks.md} §5.1: the first call to
     * {@link EEBusOhEntityHandler#applyMgcpMeasurement} creates the {@code mgcp#total-active-power}
     * Channel and updates its state, mirroring
     * {@link #whenApplyMpcMeasurementCalledThenChannelCreatedAndStateUpdated} for MGCP's
     * fixed-signature single data point (docs/ADR/040-mgcp-client-usecase.md Decision 4).
     */
    @Test
    void whenApplyMgcpMeasurementCalledThenChannelCreatedAndStateUpdated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MGCP,
                EEBusBindingConstants.CHANNEL_MGCP_TOTAL_ACTIVE_POWER);

        // Act
        handler.applyMgcpMeasurement(456.7);

        // Assert
        Channel channel = handler.getThing().getChannel(channelUID);
        assertNotNull(channel);
        State state = callback.getState(channelUID.getAsString());
        assertEquals(new QuantityType<>(456.7, Units.WATT), state);
    }

    /**
     * Covers {@code docs/changes/mgcp-client-usecase/tasks.md} §5.2: repeated calls must not
     * duplicate the Channel, mirroring {@link #whenApplyMpcMeasurementCalledTwiceThenChannelIsNotDuplicated}.
     */
    @Test
    void whenApplyMgcpMeasurementCalledTwiceThenChannelIsNotDuplicated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();

        // Act
        handler.applyMgcpMeasurement(100.0);
        handler.applyMgcpMeasurement(150.0);

        // Assert
        assertEquals(1, handler.getThing().getChannels().size());
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MGCP,
                EEBusBindingConstants.CHANNEL_MGCP_TOTAL_ACTIVE_POWER);
        assertEquals(new QuantityType<>(150.0, Units.WATT), callback.getState(channelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/dynamic-client-role-channels/specs/dynamic-channels/spec.md},
     * Scenario "MPC detection is recorded as a Thing property".
     */
    @Test
    void whenRecordDetectedUseCaseCalledThenThingPropertyIsSet() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();

        // Act
        handler.recordDetectedUseCase(EEBusBindingConstants.USE_CASE_KEY_MPC, "server");

        // Assert
        assertEquals("server", handler.getThing().getProperties().get(EEBusBindingConstants.USE_CASE_KEY_MPC));
    }

    /**
     * Covers {@code docs/changes/dynamic-client-role-channels/specs/dynamic-channels/spec.md},
     * Scenario "Channel remains after unpair()" - now exercised via a trust revocation
     * ({@link EEBusOhEntityHandler#applyStatus()}) rather than an {@code unpair()} Thing Action,
     * since trust moved to the Bridge (docs/ADR/024-oh-device-oh-entity-rename.md); applied to
     * the MPC Channel specifically (the status-transition half of that scenario is already
     * covered by {@link #whenTrustRevokedThenStatusIsOfflineAgain}).
     */
    @Test
    void whenTrustRevokedThenMpcPowerChannelAndStateRemain() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        handler.applyMpcPower(42.0);
        ChannelUID channelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_MPC,
                EEBusBindingConstants.CHANNEL_MPC_POWER);
        when(bridgeHandler.isTrusted(SKI)).thenReturn(false);

        // Act
        handler.applyStatus();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertNotNull(handler.getThing().getChannel(channelUID));
        assertEquals(new QuantityType<>(42.0, Units.WATT), callback.getState(channelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/lpc-lpp-client-role-channels/specs/dynamic-channels/spec.md},
     * Scenario "LPC status Channels appear after first successful resolution" - the
     * {@link EEBusOhEntityHandler#applyLimitStatus} half (the detection/subscription half is
     * {@code AbstractEEBusLimitEnergyGuardUseCase}'s responsibility, not exercised here, same
     * split as the MPC tests above).
     */
    @Test
    void whenApplyLimitStatusCalledForLpcThenChannelsCreatedAndStateUpdated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        ChannelUID activeChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPC,
                EEBusBindingConstants.CHANNEL_LIMIT_ACTIVE);
        ChannelUID valueChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPC,
                EEBusBindingConstants.CHANNEL_LIMIT_VALUE);
        ChannelUID durationChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPC,
                EEBusBindingConstants.CHANNEL_LIMIT_DURATION);

        // Act
        handler.applyLimitStatus(EEBusBindingConstants.CHANNEL_GROUP_LPC, true, 2500.0, 1800L);

        // Assert
        assertNotNull(handler.getThing().getChannel(activeChannelUID));
        assertNotNull(handler.getThing().getChannel(valueChannelUID));
        assertNotNull(handler.getThing().getChannel(durationChannelUID));
        assertEquals(OnOffType.ON, callback.getState(activeChannelUID.getAsString()));
        assertEquals(new QuantityType<>(2500.0, Units.WATT), callback.getState(valueChannelUID.getAsString()));
        assertEquals(new QuantityType<>(1800L, Units.SECOND), callback.getState(durationChannelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/lpc-lpp-client-role-channels/specs/dynamic-channels/spec.md},
     * Scenario "LPP status Channels appear after first successful resolution" - same behavior as
     * LPC, different Channel Group, confirming no LPC-specific special-casing crept into
     * {@link EEBusOhEntityHandler#applyLimitStatus}'s group parameterization.
     */
    @Test
    void whenApplyLimitStatusCalledForLppThenChannelsCreatedAndStateUpdated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();
        ChannelUID activeChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPP,
                EEBusBindingConstants.CHANNEL_LIMIT_ACTIVE);
        ChannelUID valueChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPP,
                EEBusBindingConstants.CHANNEL_LIMIT_VALUE);
        ChannelUID durationChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPP,
                EEBusBindingConstants.CHANNEL_LIMIT_DURATION);

        // Act - no duration written (null): LPC-004 unbounded limit, Channel shows UNDEF, not "0 s"
        handler.applyLimitStatus(EEBusBindingConstants.CHANNEL_GROUP_LPP, false, 0.0, null);

        // Assert
        assertNotNull(handler.getThing().getChannel(activeChannelUID));
        assertNotNull(handler.getThing().getChannel(valueChannelUID));
        assertNotNull(handler.getThing().getChannel(durationChannelUID));
        assertEquals(OnOffType.OFF, callback.getState(activeChannelUID.getAsString()));
        assertEquals(new QuantityType<>(0.0, Units.WATT), callback.getState(valueChannelUID.getAsString()));
        assertEquals(UnDefType.UNDEF, callback.getState(durationChannelUID.getAsString()));
    }

    /**
     * Covers {@code docs/changes/lpc-lpp-client-role-channels/tasks.md} §6.1: repeated calls
     * must not duplicate the Channels, mirroring
     * {@link #whenApplyMpcPowerCalledTwiceThenChannelIsNotDuplicated}.
     */
    @Test
    void whenApplyLimitStatusCalledTwiceThenChannelsAreNotDuplicated() {
        // Arrange
        EEBusOhEntityHandler handler = createHandlerWithOnlineBridge(true);
        handler.initialize();
        callback.waitForOnline();

        // Act
        handler.applyLimitStatus(EEBusBindingConstants.CHANNEL_GROUP_LPC, true, 1000.0, 900L);
        handler.applyLimitStatus(EEBusBindingConstants.CHANNEL_GROUP_LPC, false, 500.0, null);

        // Assert
        assertEquals(3, handler.getThing().getChannels().size());
        ChannelUID valueChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPC,
                EEBusBindingConstants.CHANNEL_LIMIT_VALUE);
        ChannelUID durationChannelUID = new ChannelUID(OH_ENTITY_UID, EEBusBindingConstants.CHANNEL_GROUP_LPC,
                EEBusBindingConstants.CHANNEL_LIMIT_DURATION);
        assertEquals(new QuantityType<>(500.0, Units.WATT), callback.getState(valueChannelUID.getAsString()));
        assertEquals(UnDefType.UNDEF, callback.getState(durationChannelUID.getAsString()));
    }
}
