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
 * The {@link ResourceListEntryDto} maps a single entry of the list-style PointT resources used by
 * {@code ChildThingDiscoveryService} and {@code HeatpumpHandler} to enumerate circuits/zones -
 * {@code resource/heatingCircuits}, {@code resource/dhwCircuits}, {@code resource/solarCircuits},
 * and {@code resource/zones/list}.
 *
 * <p>
 * TODO ($Dev): the exact JSON field name and shape of these list responses were not enumerated in
 * either reverse-engineering source this project is based on ({@code buderus-reverse.md},
 * {@code myapp-api-analysis.md}) - only the endpoints and their purpose are documented. This
 * assumes an array of objects with an {@code id} field (e.g. {@code "hc1"}, {@code "dhw1"}); if a
 * live gateway returns a different shape (e.g. a bare array of id Strings), this DTO and the
 * {@code PointTApiClient.listResourceIds(...)} caller must be adjusted before release. This is a
 * plain Gson deserialization target, not a public API type, so it intentionally does not carry
 * {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class ResourceListEntryDto {

    public @Nullable String id;
}
