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
package org.openhab.binding.curves.internal.handler;

import static org.mockito.Mockito.mock;
import static org.openhab.binding.curves.internal.CurvesConstants.THING_TYPE_HEMS;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.items.DateTimeItem;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.internal.BridgeImpl;

/**
 * The {@link ControllerMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ControllerMock {

    public static HemsBridge createController() {
        BridgeImpl bi = new BridgeImpl(THING_TYPE_HEMS, "curves-bridge");
        Map<String, Object> controllerConf = new HashMap<>();
        controllerConf.put("sunriseItem", "sunrise");
        controllerConf.put("sunsetItem", "sunset");
        bi.setConfiguration(new Configuration(controllerConf));

        ItemRegistry itemRegistry = createItemRegistry();
        HemsBridge controller = new HemsBridge(bi, itemRegistry, mock(PersistenceServiceRegistry.class),
                mock(TimeZoneProvider.class));
        return controller;
    }

    private static ItemRegistry createItemRegistry() {
        ItemRegistry itemRegistry = new ItemRegistryMock();

        DateTimeItem sunriseItem = new DateTimeItem("sunrise");
        String sunriseDT = "2025-06-27T05:16:00.000+02:00";
        // 2025-05-19T02:00:00.000+02:00
        sunriseItem.setState(new DateTimeType(Instant.parse(sunriseDT)));

        DateTimeItem sunsetItem = new DateTimeItem("sunset");
        String sunsetDT = "2025-06-27T21:42:00.000+02:00";
        sunsetItem.setState(new DateTimeType(Instant.parse(sunsetDT)));

        itemRegistry.add(sunriseItem);
        itemRegistry.add(sunsetItem);
        return itemRegistry;
    }
}
