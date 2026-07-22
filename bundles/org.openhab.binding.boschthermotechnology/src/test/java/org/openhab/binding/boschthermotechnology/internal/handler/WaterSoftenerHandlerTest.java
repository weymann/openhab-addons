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
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_WATER_SOFTENER;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openhab.binding.boschthermotechnology.internal.mock.CallbackMock;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ThingBuilder;

/**
 * {@link WaterSoftenerHandler} is an intentional placeholder (ADR-006) - it never polls, so its
 * only two meaningful states are covered here: a configured thing going {@code OFFLINE}/
 * {@code CONFIGURATION_PENDING} (the "not implemented yet" placeholder state, standing in for the
 * usual happy path since there is no ONLINE state to reach), and a misconfigured thing going
 * {@code OFFLINE}/{@code CONFIGURATION_ERROR}.
 *
 * @author Bernd Weymann - Initial contribution
 */
class WaterSoftenerHandlerTest {

    private static final ThingUID WATER_SOFTENER_UID = new ThingUID(THING_TYPE_WATER_SOFTENER, "ws1");

    private CallbackMock callback = new CallbackMock();
    private WaterSoftenerHandler handler = createHandler(Map.of(CONFIG_GATEWAY_ID, "gw1"));

    private WaterSoftenerHandler createHandler(Map<String, Object> config) {
        callback = new CallbackMock();
        Thing thing = ThingBuilder.create(THING_TYPE_WATER_SOFTENER, WATER_SOFTENER_UID)
                .withConfiguration(new Configuration(config)).build();
        WaterSoftenerHandler newHandler = new WaterSoftenerHandler(thing);
        newHandler.setCallback(callback);
        return newHandler;
    }

    @Test
    void whenGatewayIdIsSet_thenThingGoesOfflineWithConfigurationPending() {
        // Arrange
        handler = createHandler(Map.of(CONFIG_GATEWAY_ID, "gw1"));

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertEquals(ThingStatusDetail.CONFIGURATION_PENDING, callback.getStatus().getStatusDetail());
    }

    @Test
    void whenGatewayIdIsMissing_thenThingGoesOfflineWithConfigurationError() {
        // Arrange
        handler = createHandler(Map.of());

        // Act
        handler.initialize();

        // Assert
        callback.waitForStatus(ThingStatus.OFFLINE);
        assertEquals(ThingStatusDetail.CONFIGURATION_ERROR, callback.getStatus().getStatusDetail());
    }
}
