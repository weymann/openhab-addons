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
package org.openhab.binding.curves.internal.interfaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.TimeSeries;

/**
 * The interface {@link Battery} defines the functionality a PV production entity shall provide
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface Battery extends CurvesEntity {
    public enum BatteryProperty {
        CAPACITY,
        CHARGE,
        SOC,
        MAX_CHARGE_CAPABILITY,
        MAX_DISCHARGE_CAPABILITY
    }

    public double getStateOfCharge();

    public double getCapacity();

    public double getCharge();

    public long getMaximumChargePower();

    public long getMaximumDischargePower();

    public void setCargeTimeSeries(TimeSeries charge);

    public void setDischargeTimeSeries(TimeSeries discharge);
}
