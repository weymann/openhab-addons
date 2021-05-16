/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.ems.internal.dto;

import java.util.List;

/**
 * {@link OnecallWeather} Data Transfer Object
 *
 * @author Bernd Weymann - Initial contribution
 */
public class OnecallWeather {
    public double lat; // ": 51.44,
    public double lon; // ": -10,
    public String timezone; // ": "Europe/Dublin",
    public int timezone_offset; // ": 3600,
    public WeatherCurrent current;
    public List<HourForecast> hourly;
    public List<DailyForecast> daily;
}
