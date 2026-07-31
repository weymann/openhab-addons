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
 * The {@link ResourceListEntryDto} maps a single entry of the {@code references} array nested
 * inside a PointT {@code refEnum}-typed resource - the actual shape returned by the list-style
 * resources used by {@code ChildThingDiscoveryService} and {@code HeatpumpHandler} to enumerate
 * circuits/zones ({@code resource/heatingCircuits}, {@code resource/dhwCircuits},
 * {@code resource/solarCircuits}, {@code resource/zones/list}). Confirmed against a live gateway:
 *
 * <pre>{@code
 * {
 *   "id": "/heatingCircuits",
 *   "type": "refEnum",
 *   "references": [ { "id": "/heatingCircuits/hc1", "uri": "http://k40/heatingCircuits/hc1" } ]
 * }
 * }</pre>
 *
 * <p>
 * {@code id} here is the full resource path of the referenced entry (e.g.
 * {@code /heatingCircuits/hc1}), not the bare circuit id - {@code PointTApiClient.listResourceIds}
 * takes the last path segment. See {@link ResourceReferenceListDto} for the enclosing object. This
 * is a plain Gson deserialization target, not a public API type, so it intentionally does not
 * carry {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class ResourceListEntryDto {

    public @Nullable String id;
    public @Nullable String uri;
}
