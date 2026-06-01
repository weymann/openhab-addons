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
 * DTO representing a single weather observation from the BrightSky API.
 * Used by both {@code /current_weather} and {@code /weather} endpoints.
 *
 * <p>
 * Field names use {@code camelCase} matching Gson's
 * {@code LOWER_CASE_WITH_UNDERSCORES} naming policy for automatic
 * {@code snake_case} mapping. Fields annotated with {@code @Nullable}
 * may be absent or {@code null} in the API response.
 *
 * @author Bernd Weymann - Initial contribution
 */
public class WeatherRecord {

    public @Nullable String timestamp;
    public @Nullable Integer sourceId;

    // Temperature
    public @Nullable Double temperature;
    public @Nullable Double dewPoint;

    // Humidity and pressure
    public @Nullable Integer relativeHumidity;
    public @Nullable Double pressureMsl;

    // Wind - 10-minute averages (used as primary channels)
    public @Nullable Double windSpeed10;
    public @Nullable Integer windDirection10;
    public @Nullable Double windGustSpeed10;
    public @Nullable Integer windGustDirection10;

    // Wind - 30 and 60-minute averages (available but not exposed as channels in MVP)
    public @Nullable Double windSpeed30;
    public @Nullable Integer windDirection30;
    public @Nullable Double windGustSpeed30;
    public @Nullable Integer windGustDirection30;
    public @Nullable Double windSpeed60;
    public @Nullable Integer windDirection60;
    public @Nullable Double windGustSpeed60;
    public @Nullable Integer windGustDirection60;

    // Precipitation - 10-minute sum
    public @Nullable Double precipitation10;
    public @Nullable Double precipitation30;
    public @Nullable Double precipitation60;

    // Sky
    public @Nullable Integer cloudCover;
    public @Nullable Integer visibility;

    // Sunshine and solar irradiance
    public @Nullable Double sunshine30;
    public @Nullable Double sunshine60;
    public @Nullable Double solar10;
    public @Nullable Double solar30;
    public @Nullable Double solar60;

    // Condition and icon
    public @Nullable String condition;
    public @Nullable String icon;
}
