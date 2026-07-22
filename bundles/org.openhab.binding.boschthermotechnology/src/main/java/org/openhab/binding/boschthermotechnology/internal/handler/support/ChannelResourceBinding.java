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

import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

import com.google.gson.JsonElement;

/**
 * The {@link ChannelResourceBinding} declaratively ties one channel id to one PointT resource
 * path, a reader that turns the resource's raw JSON value into a channel {@link State}, and -
 * for writable channels - a writer that turns an incoming {@link Command} back into the raw JSON
 * value to {@code PUT}. This is the declarative input {@code ResourcePollingSupport} is driven by
 * - see ADR-006.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public record ChannelResourceBinding(String channelId, String resourcePath, Function<JsonElement, State> stateReader,
        @Nullable Function<Command, JsonElement> commandWriter) {

    public static ChannelResourceBinding readOnly(String channelId, String resourcePath,
            Function<JsonElement, State> stateReader) {
        return new ChannelResourceBinding(channelId, resourcePath, stateReader, null);
    }

    public static ChannelResourceBinding readWrite(String channelId, String resourcePath,
            Function<JsonElement, State> stateReader, Function<Command, JsonElement> commandWriter) {
        return new ChannelResourceBinding(channelId, resourcePath, stateReader, commandWriter);
    }

    public boolean isWriteable() {
        return commandWriter != null;
    }
}
