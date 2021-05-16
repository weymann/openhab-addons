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
 * {@link WeatherCurrent} Data Transfer Object
 *
 * @author Bernd Weymann - Initial contribution
 */
public class WeatherCurrent {
    public int dt; // ": 1620509255,
    public int sunrisedt; // ": 1620449957,
    public int sunsetdt; // ": 1620504821,
    public double tempdt; // ": 284.43,
    public double feels_likedt; // ": 283.73,
    public int pressuredt; // ": 987,
    public int humiditydt; // ": 81,
    public double dew_pointdt; // ": 281.29,
    public int uvidt; // ": 0,
    public int cloudsdt; // ": 65,
    public int visibilitydt; // ": 10000,
    public double wind_speeddt; // ": 13.78,
    public int wind_degdt; // ": 190,
    public double wind_gustdt; // ": 17.82,
    public List<WeatherDescription> weather;
}
