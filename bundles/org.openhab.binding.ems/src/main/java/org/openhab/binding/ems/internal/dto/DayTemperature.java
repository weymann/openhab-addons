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

/**
 * {@link DayTemperature} Data Transfer Object
 *
 * @author Bernd Weymann - Initial contribution
 */
public class DayTemperature {
    public double day; // ":284.62,
    public double min; // ":283.83,
    public double max; // ":285.33,
    public double night; // ":284.4,
    public double eve; // ":284.54,
    public double morn; // ":284.62
}
