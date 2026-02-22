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
package org.openhab.binding.energychartsinfo.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EnergyChartsInfoConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EnergyChartsInfoConfiguration {

    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String zone = "";
    public String country = "";
    public String token = "";
    public double fixCost = 0.0;
    public double vat = 0.0;
    public String resolution = "PT15M";
    public long refreshInterval = 120;
}
