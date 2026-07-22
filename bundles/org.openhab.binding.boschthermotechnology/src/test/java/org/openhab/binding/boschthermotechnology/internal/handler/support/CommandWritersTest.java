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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

import com.google.gson.JsonElement;

/**
 * Parameterized unit tests for the {@code Command -> JsonElement} writer functions in
 * {@link CommandWriters}, per the mapping-table testing rule in {@code rules/testing-rules.md}.
 *
 * @author Bernd Weymann - Initial contribution
 */
class CommandWritersTest {

    @Test
    void numberAcceptsQuantityTypeAndWritesPlainDouble() {
        JsonElement value = CommandWriters.number(new QuantityType<>(21.5, SIUnits.CELSIUS));
        assertEquals(21.5, value.getAsDouble());
    }

    @Test
    void numberAcceptsDecimalTypeAndWritesPlainDouble() {
        JsonElement value = CommandWriters.number(new DecimalType(3));
        assertEquals(3.0, value.getAsDouble());
    }

    @Test
    void numberRejectsUnsupportedCommandType() {
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.number(new StringType("nope")));
    }

    @ParameterizedTest
    @EnumSource(OnOffType.class)
    void onOffAcceptsOnOffTypeAndWritesBoolean(OnOffType command) {
        JsonElement value = CommandWriters.onOff(command);
        assertEquals(command == OnOffType.ON, value.getAsBoolean());
    }

    @Test
    void onOffRejectsUnsupportedCommandType() {
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.onOff(new DecimalType(1)));
    }

    @Test
    void stringAcceptsStringTypeAndWritesRawValue() {
        JsonElement value = CommandWriters.string(new StringType("Eco"));
        assertEquals("Eco", value.getAsString());
    }

    @Test
    void stringRejectsUnsupportedCommandType() {
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.string(new DecimalType(1)));
    }

    @Test
    void allWritersRejectRefreshTypeSinceItIsNeverAWriteCommand() {
        Command refresh = RefreshType.REFRESH;
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.number(refresh));
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.onOff(refresh));
        assertThrows(IllegalArgumentException.class, () -> CommandWriters.string(refresh));
    }
}
