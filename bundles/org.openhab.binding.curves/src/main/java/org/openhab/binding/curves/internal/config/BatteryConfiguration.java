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
 * The {@link BatteryConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BatteryConfiguration {
    public double batteryCapacity = 10;
    public long maxChargePower = 3000;
    public long maxDischargePower = 3000;

    public String stateOfChargeItem = "";
    public String stateOfChargeItemPersistence = "";
    public String chargeItem = "";
    public String chargeItemPersistence = "";
    public String dischargeItem = "";
    public String dischargeItemPersistence = "";
}
