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
package org.openhab.binding.eebus.internal.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_NETWORK;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_PEER;

import java.util.List;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

/**
 * Unit tests for {@link EEBusMdnsDiscoveryParticipant}, covering the scenarios from
 * {@code docs/changes/eebus-network-discovery/specs/discovery/spec.md}.
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
class EEBusMdnsDiscoveryParticipantTest {

    private static final String SKI = "dd8ba427c0a449180513fbb51bf6f0bbdbf0c821";
    private static final ThingUID NETWORK_UID = new ThingUID(THING_TYPE_NETWORK, "network");

    private ThingRegistry thingRegistry = mock(ThingRegistry.class);
    private EEBusMdnsDiscoveryParticipant participant = new EEBusMdnsDiscoveryParticipant(thingRegistry);

    @BeforeEach
    void setUp() {
        thingRegistry = mock(ThingRegistry.class);
        participant = new EEBusMdnsDiscoveryParticipant(thingRegistry);
    }

    @Test
    void whenNoNetworkBridgeExistsThenCreateResultReturnsNull() {
        // Arrange
        when(thingRegistry.getAll()).thenReturn(List.of());
        ServiceInfo service = mockServiceInfo(SKI, "Hager Energy", "S10");

        // Act
        DiscoveryResult result = participant.createResult(service);

        // Assert
        assertNull(result);
    }

    @Test
    void whenNetworkBridgeExistsThenCreateResultUsesItsUid() {
        // Arrange
        Thing networkThing = mockThing(THING_TYPE_NETWORK, NETWORK_UID, new Configuration());
        when(thingRegistry.getAll()).thenReturn(List.of(networkThing));
        ServiceInfo service = mockServiceInfo(SKI, "Hager Energy", "S10");

        // Act
        DiscoveryResult result = participant.createResult(service);

        // Assert
        assertNotNull(result);
        assertEquals(NETWORK_UID, result.getBridgeUID());
        assertEquals(SKI, result.getProperties().get("ski"));
        assertEquals("Hager Energy S10", result.getLabel());
    }

    @Test
    void whenSkiIsBlankThenCreateResultReturnsNull() {
        // Arrange
        Thing networkThing = mockThing(THING_TYPE_NETWORK, NETWORK_UID, new Configuration());
        when(thingRegistry.getAll()).thenReturn(List.of(networkThing));
        ServiceInfo service = mockServiceInfo("", "Hager Energy", "S10");

        // Act
        DiscoveryResult result = participant.createResult(service);

        // Assert
        assertNull(result);
    }

    @Test
    void whenSkiAlreadyBelongsToPeerThenCreateResultReturnsNull() {
        // Arrange
        Thing networkThing = mockThing(THING_TYPE_NETWORK, NETWORK_UID, new Configuration());

        Configuration peerConfiguration = new Configuration();
        peerConfiguration.put("ski", SKI);
        Thing peerThing = mockThing(THING_TYPE_PEER, new ThingUID(THING_TYPE_PEER, NETWORK_UID, SKI),
                peerConfiguration);

        when(thingRegistry.getAll()).thenReturn(List.of(networkThing, peerThing));
        ServiceInfo service = mockServiceInfo(SKI, "Hager Energy", "S10");

        // Act
        DiscoveryResult result = participant.createResult(service);

        // Assert
        assertNull(result);
    }

    @Test
    void whenBrandAndModelAreBlankThenLabelFallsBackToServiceName() {
        // Arrange
        Thing networkThing = mockThing(THING_TYPE_NETWORK, NETWORK_UID, new Configuration());
        when(thingRegistry.getAll()).thenReturn(List.of(networkThing));
        ServiceInfo service = mockServiceInfo(SKI, "", "");
        when(service.getName()).thenReturn("KeoApp");

        // Act
        DiscoveryResult result = participant.createResult(service);

        // Assert
        assertNotNull(result);
        assertEquals("KeoApp", result.getLabel());
    }

    private static ServiceInfo mockServiceInfo(String ski, String brand, String model) {
        ServiceInfo service = mock(ServiceInfo.class);
        when(service.getPropertyString("ski")).thenReturn(ski);
        when(service.getPropertyString("brand")).thenReturn(brand);
        when(service.getPropertyString("model")).thenReturn(model);
        when(service.getPropertyString("type")).thenReturn("CEM");
        when(service.getName()).thenReturn("KeoApp");
        return service;
    }

    private static Thing mockThing(ThingTypeUID thingTypeUID, ThingUID thingUID, Configuration configuration) {
        Thing thing = mock(Thing.class);
        when(thing.getThingTypeUID()).thenReturn(thingTypeUID);
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(configuration);
        return thing;
    }
}
