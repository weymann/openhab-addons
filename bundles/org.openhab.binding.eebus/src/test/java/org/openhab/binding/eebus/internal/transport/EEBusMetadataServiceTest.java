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
package org.openhab.binding.eebus.internal.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;

/**
 * Unit tests for {@link EEBusMetadataService#find}, covering the scenarios from
 * {@code docs/changes/separate-real-and-oh-peer-things/specs/server-metadata/spec.md}.
 *
 * <p>
 * {@code @SuppressWarnings("null")}: Mockito is not designed with null type annotations in mind,
 * so combining it with this {@code @NonNullByDefault} test class produces "unsafe
 * interpretation" compiler advisories with no null-safety benefit.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
class EEBusMetadataServiceTest {

    private MetadataRegistry metadataRegistry = mock(MetadataRegistry.class);
    private ItemRegistry itemRegistry = mock(ItemRegistry.class);
    private EventPublisher eventPublisher = mock(EventPublisher.class);
    private EEBusMetadataService service = new EEBusMetadataService(metadataRegistry, itemRegistry, eventPublisher);

    @BeforeEach
    void setUp() {
        metadataRegistry = mock(MetadataRegistry.class);
        itemRegistry = mock(ItemRegistry.class);
        eventPublisher = mock(EventPublisher.class);
        service = new EEBusMetadataService(metadataRegistry, itemRegistry, eventPublisher);
    }

    @Test
    void whenTwoServicesOfferSameUseCaseThenFindResolvesCorrectService() {
        // Arrange
        Metadata ems1Metadata = metadata("WR_Leistung_1", "ems1:MPC.power");
        Metadata ems2Metadata = metadata("WR_Leistung_2", "ems2:MPC.power");
        when(metadataRegistry.getAll()).thenReturn(List.of(ems1Metadata, ems2Metadata));

        // Act
        Optional<Metadata> forEms1 = service.find("ems1", "MPC", "power");
        Optional<Metadata> forEms2 = service.find("ems2", "MPC", "power");

        // Assert
        assertTrue(forEms1.isPresent());
        assertEquals("WR_Leistung_1", EEBusMetadataService.itemNameOf(forEms1.get()));
        assertTrue(forEms2.isPresent());
        assertEquals("WR_Leistung_2", EEBusMetadataService.itemNameOf(forEms2.get()));
    }

    @Test
    void whenMetadataValueMissingOhServiceIdPrefixThenFindIgnoresIt() {
        // Arrange
        Metadata malformedMetadata = metadata("WR_Leistung", "MPC.power");
        when(metadataRegistry.getAll()).thenReturn(List.of(malformedMetadata));

        // Act
        Optional<Metadata> result = service.find("ems1", "MPC", "power");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void whenNoMetadataDeclaresUseCaseThenFindReturnsEmpty() {
        // Arrange
        when(metadataRegistry.getAll()).thenReturn(List.of());

        // Act
        Optional<Metadata> result = service.find("ems1", "MPC", "power");

        // Assert
        assertTrue(result.isEmpty());
    }

    private static Metadata metadata(String itemName, String value) {
        return new Metadata(new MetadataKey(EEBusMetadataService.NAMESPACE, itemName), value, Map.of());
    }
}
