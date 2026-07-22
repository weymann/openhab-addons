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
package org.openhab.binding.boschthermotechnology.internal.handler.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

import com.google.gson.JsonPrimitive;

/**
 * Parameterized unit tests for the {@code JsonElement -> State} reader functions in
 * {@link StateMappers}, per the mapping-table testing rule in {@code rules/testing-rules.md}.
 *
 * @author Bernd Weymann - Initial contribution
 */
class StateMappersTest {

    @ParameterizedTest
    @CsvSource({ "21.5, 21.5", "0, 0.0", "-5.3, -5.3" })
    void temperatureMapsRawNumberToCelsiusQuantity(double raw, double expected) {
        State state = StateMappers.temperature(new JsonPrimitive(raw));
        assertEquals(new org.openhab.core.library.types.QuantityType<>(expected, SIUnits.CELSIUS), state);
    }

    @ParameterizedTest
    @CsvSource({ "60, 60.0", "0, 0.0" })
    void minutesMapsRawNumberToMinuteQuantity(double raw, double expected) {
        State state = StateMappers.minutes(new JsonPrimitive(raw));
        assertEquals(new org.openhab.core.library.types.QuantityType<>(expected, Units.MINUTE), state);
    }

    @ParameterizedTest
    @CsvSource({ "true, ON", "false, OFF" })
    void onOffMapsBooleanPrimitiveDirectly(boolean raw, OnOffType expected) {
        State state = StateMappers.onOff(new JsonPrimitive(raw));
        assertEquals(expected, state);
    }

    @ParameterizedTest
    @CsvSource({ "1, ON", "0, OFF" })
    void onOffFallsBackToNumericZeroOneWhenNotBoolean(int raw, OnOffType expected) {
        // Defensive fallback documented as a TODO in StateMappers.onOff(...) - the gateway's
        // actual boolean-vs-0/1 shape for these resources is unconfirmed, see the code comment.
        State state = StateMappers.onOff(new JsonPrimitive(raw));
        assertEquals(expected, state);
    }

    @org.junit.jupiter.api.Test
    void rawStringUsesPrimitiveValueWhenJsonIsPrimitive() {
        State state = StateMappers.rawString(new JsonPrimitive("Comfort"));
        assertEquals(new org.openhab.core.library.types.StringType("Comfort"), state);
    }

    @org.junit.jupiter.api.Test
    void rawStringFallsBackToJsonToStringWhenNotPrimitive() {
        com.google.gson.JsonObject object = new com.google.gson.JsonObject();
        object.addProperty("nested", "value");
        State state = StateMappers.rawString(object);
        assertEquals(new org.openhab.core.library.types.StringType(object.toString()), state);
    }
}
