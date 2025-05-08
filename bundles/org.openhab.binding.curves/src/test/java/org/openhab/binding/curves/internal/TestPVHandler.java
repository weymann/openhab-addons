/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.openhab.binding.curves.internal.CurvesConstants.THING_TYPE_HEMS;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.handler.PVHandler;
import org.openhab.binding.curves.internal.handler.PVHandlerMock;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.internal.BridgeImpl;

/**
 * The {@link TestPVHandler} for curve generation use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestPVHandler {

    @Test
    void testPVForecast() {
        BridgeImpl bi = new BridgeImpl(THING_TYPE_HEMS, "curves-bridge");
        HemsBridge controller = new HemsBridge(bi, mock(ItemRegistry.class), mock(PersistenceServiceRegistry.class),
                mock(TimeZoneProvider.class));
        PVHandler pvH = new PVHandlerMock();
        controller.registerEntity(pvH);
        String forecastFile = "src/test/resources/forecastsolar/forecast-response.json";
        try {
            String forecastContent = new String(Files.readAllBytes(Paths.get(forecastFile)));
        } catch (Exception e) {
            fail();
        }
    }
}
