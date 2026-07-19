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
package org.openhab.binding.boschthermotechnology.internal.dto;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * The {@link ResourceDto} maps a PointT resource object as returned by
 * {@code GET /gateways/{gatewayId}/resource/{path}} and as required for the full-object fallback
 * payload of {@code PUT /gateways/{gatewayId}/resource/{path}}.
 *
 * <p>
 * Field names ({@code id}, {@code type}, {@code writeable}, {@code value}) are confirmed by the
 * reverse-engineering analysis ({@code buderus-reverse.md}, section 1 and
 * {@code BuderusPointTClient.set_resource_value()}). {@code value} is kept as a raw
 * {@link JsonElement} because it can be a number, string, or boolean depending on the resource.
 * This is a plain Gson deserialization target, not a public API type, so it intentionally does
 * not carry {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class ResourceDto {

    public @Nullable String id;
    public @Nullable String type;
    public int writeable;
    public @Nullable JsonElement value;

    public ResourceDto() {
    }

    public ResourceDto(@Nullable String id, @Nullable String type, int writeable, @Nullable JsonElement value) {
        this.id = id;
        this.type = type;
        this.writeable = writeable;
        this.value = value;
    }

    /**
     * @return {@code true} if the gateway reports this resource as writeable ({@code writeable == 1}).
     */
    public boolean isWriteable() {
        return writeable == 1;
    }
}
