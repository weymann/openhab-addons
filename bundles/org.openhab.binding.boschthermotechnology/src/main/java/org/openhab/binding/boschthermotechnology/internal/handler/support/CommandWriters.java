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
import org.openhab.core.types.Command;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * The {@link CommandWriters} class collects the generic {@code Command -> JsonElement} writer
 * functions every handler's writable {@code ChannelResourceBinding}s are built from, factored out
 * of the {@code GatewayHandler}-only {@code toJsonValue(...)} method that existed before ADR-006.
 * Domain-specific mappings with their own enumeration (e.g. the DHW operation mode Number-code
 * mapping) stay local to the handler that owns them ({@code HeatpumpHandler}) rather than living
 * here, since they are not generic conversions.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public final class CommandWriters {

    private CommandWriters() {
        // static factory methods only
    }

    /**
     * Accepts {@link QuantityType} or {@link DecimalType} commands, writing the plain numeric
     * value (unit conversion, if any, is expected to already have happened via the channel's item
     * type / {@code %unit%} pattern).
     *
     * @throws IllegalArgumentException if {@code command} is neither a {@link QuantityType} nor a
     *             {@link DecimalType}
     */
    public static JsonElement number(Command command) {
        if (command instanceof QuantityType<?> quantity) {
            return new JsonPrimitive(quantity.doubleValue());
        }
        if (command instanceof DecimalType decimal) {
            return new JsonPrimitive(decimal.doubleValue());
        }
        throw new IllegalArgumentException("Unsupported command type " + command.getClass().getSimpleName());
    }

    /**
     * @throws IllegalArgumentException if {@code command} is not an {@link OnOffType}
     */
    public static JsonElement onOff(Command command) {
        if (command instanceof OnOffType onOff) {
            return new JsonPrimitive(onOff == OnOffType.ON);
        }
        throw new IllegalArgumentException("Unsupported command type " + command.getClass().getSimpleName());
    }

    /**
     * @throws IllegalArgumentException if {@code command} is not a {@link StringType}
     */
    public static JsonElement string(Command command) {
        if (command instanceof StringType stringType) {
            return new JsonPrimitive(stringType.toString());
        }
        throw new IllegalArgumentException("Unsupported command type " + command.getClass().getSimpleName());
    }
}
