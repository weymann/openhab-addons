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
package org.openhab.binding.curves.internal.strategies;

import java.time.Instant;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.scheduler.ScheduleEntry;

/**
 * The {@link GridFriendlyChargingStrategy} class contains fields mapping thing configuration parameters.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GridFriendlyChargingStrategy extends Strategy {
    public List<ScheduleEntry> chargeTimes;

    GridFriendlyChargingStrategy(List<ScheduleEntry> chargeTimes) {
        this.chargeTimes = chargeTimes;
    }

    @Override
    public Type getStrategyType() {
        return Strategy.Type.GRID_FRIENDLY_BATTERY_CHARGING;
    }

    @Override
    public boolean isActive(Instant timestamp) {
        for (ScheduleEntry entry : chargeTimes) {
            if (!timestamp.isBefore(entry.start) && !timestamp.isAfter(entry.end)) {
                return true;
            }
        }
        return false;
    }

    public boolean isHomeBatteryChargingBlocked(Instant time) {
        return isActive(time);
    }
}
