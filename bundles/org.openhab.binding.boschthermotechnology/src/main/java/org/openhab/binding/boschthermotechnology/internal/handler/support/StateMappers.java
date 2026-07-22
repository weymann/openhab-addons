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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

import com.google.gson.JsonElement;

/**
 * The {@link StateMappers} class collects the {@code JsonElement -> State} reader functions
 * every handler's {@code ChannelResourceBinding}s are built from, factored out of the
 * {@code GatewayHandler}-only private methods that existed before ADR-006 so all nine handlers
 * (the {@code gateway} bridge and its eight children) can reuse them instead of re-implementing
 * the same conversions.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public final class StateMappers {

    private StateMappers() {
        // static factory methods only
    }

    public static State temperature(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), SIUnits.CELSIUS);
    }

    public static State minutes(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.MINUTE);
    }

    public static State hours(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.HOUR);
    }

    public static State power(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.WATT);
    }

    public static State energy(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.KILOWATT_HOUR);
    }

    public static State count(JsonElement value) {
        return new DecimalType(value.getAsDouble());
    }

    /**
     * Serializes any resource value to a String channel, regardless of its JSON shape. Used for
     * resources whose exact value type is not yet confirmed against a live gateway - see the
     * corresponding channel-type TODOs in {@code thing-types.xml}.
     */
    public static State rawString(JsonElement value) {
        return new StringType(value.isJsonPrimitive() ? value.getAsString() : value.toString());
    }

    public static State onOff(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return OnOffType.from(value.getAsBoolean());
        }
        // TODO ($Dev): confirm whether the gateway actually reports these as booleans or as 0/1
        // numbers before release - handling both defensively for now.
        return OnOffType.from(value.getAsInt() != 0);
    }
}
