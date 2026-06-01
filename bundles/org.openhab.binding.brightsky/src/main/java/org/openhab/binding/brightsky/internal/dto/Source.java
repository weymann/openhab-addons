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
package org.openhab.binding.brightsky.internal.dto;

import org.eclipse.jdt.annotation.Nullable;

/**
 * DTO representing a DWD weather station source returned by the BrightSky API.
 *
 * @author Bernd Weymann - Initial contribution
 */
public class Source {

    public @Nullable Integer id;
    public @Nullable String dwdStationId;
    public @Nullable String observationType;
    public @Nullable Double lat;
    public @Nullable Double lon;
    public @Nullable Double height;
    public @Nullable String stationName;
    public @Nullable String wmoStationId;
    public @Nullable Double distance;
}
