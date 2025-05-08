/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link GridConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GridConfiguration {
    public double staticTariff = 0;
    public double pvSelfConsumptionTariff = 0;
    public double feedInTariff = 0;

    public String dynamicTariffItem = "";
    public String dynamicTariffItemPersistence = "";

    public String grid2HomeItem = "";
    public String grid2HomeItemPersistence = "";
    public String home2GridItem = "";
    public String home2GridPersistence = "";
}
