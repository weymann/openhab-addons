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
 * {@link HourForecast} Data Transfer Object
 *
 * @author Bernd Weymann - Initial contribution
 */
public class HourForecast {
    public int dt; // ":1620507600,
    public double temp; // ":284.43,
    public double feels_like; // ":283.73,
    public int pressure; // ":987,
    public int humidity; // ":81,
    public double dew_point; // ":281.29,
    public double uvi; // ":0,
    public int clouds; // ":65,
    public int visibility; // ":10000,
    public double wind_speed; // ":13.78,
    public int wind_deg; // ":190,
    public double wind_gust; // ":17.82,
    public List<WeatherDescription> weather; // ":[{"id":500,"main":"Rain","description":"light rain","icon":"10n"}],
    public double pop; // ":0.68,
    public HourRain rain; // ":{"1h":0.23}
}
