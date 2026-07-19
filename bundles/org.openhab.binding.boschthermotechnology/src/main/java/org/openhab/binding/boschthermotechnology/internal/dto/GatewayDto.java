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

/**
 * The {@link GatewayDto} maps a single entry of the {@code GET /gateways/} response and the
 * detail response of {@code GET /gateways/{gatewayId}}.
 *
 * <p>
 * TODO ($Dev): the exact JSON field names were not enumerated in the reverse-engineering
 * analysis (only the endpoints and their purpose are documented, not the response schema) -
 * verify field names against a real PointT response and adjust before release. This is a plain
 * Gson deserialization target, not a public API type, so it intentionally does not carry
 * {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class GatewayDto {

    public @Nullable String id;
    public @Nullable String deviceType;
    public @Nullable String firmwareVersion;
    public @Nullable String hardwareVersion;
}
