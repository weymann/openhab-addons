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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link ResourceReferenceListDto} maps a PointT resource of {@code type: "refEnum"}, as
 * returned by the list-style resources used by {@code ChildThingDiscoveryService} and
 * {@code HeatpumpHandler} to enumerate circuits/zones ({@code resource/heatingCircuits},
 * {@code resource/dhwCircuits}, {@code resource/solarCircuits}, {@code resource/zones/list}).
 * Confirmed against a live gateway - see {@link ResourceListEntryDto} for the shape of one
 * {@code references} entry. Unlike {@link ResourceDto}, this is returned directly as the response
 * body (not wrapped), and is only ever read, never written.
 *
 * <p>
 * This is a plain Gson deserialization target, not a public API type, so it intentionally does not
 * carry {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class ResourceReferenceListDto {

    public @Nullable String id;
    public @Nullable String type;
    public @Nullable List<ResourceListEntryDto> references;
}
