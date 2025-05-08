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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The interface {@link Strategy} is the marker interface for all strategies
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public abstract class Strategy {
    public enum Type {
        GRID_FRIENDLY_BATTERY_CHARGING,
        COST_OPTIMIZED_BATTERY_DISCHARGING,
        BEV_SUN_CHARGING,
        BEV_CONSTANT_CHARGING,
        BEV_MAX_CHARGING
    }

    /**
     * Default returns no blocking. Shall be overridden by concrete Strategy
     *
     * @param timestamp to evaluate
     * @return true if allowed, else otherwise
     */
    public boolean isHomeBatteryChargingAllowed(Instant timestamp) {
        return true;
    }

    /**
     * Default returns no blocking. Shall be overridden by concrete Strategy
     *
     * @param timestamp to evaluate
     * @return true if allowed, else otherwise
     */
    public boolean isHomeBatteryDischargingAllowed(Instant timestamp) {
        return true;
    }

    /**
     * Default returns no addon power. Shall be overridden by concrete Strategy
     *
     * @param timestamp to evaluate
     * @return additional power for consumption
     */
    public int powerAdd(Instant timestamp) {
        return 0;
    }

    public void addCollision(Instant timestamp, Collision collision) {
        // Default implementation does nothing
    }

    @Override
    public String toString() {
        return "{\"type\":\"" + getStrategyType().name() + "\"}"; // JSON representation}";
    }

    public abstract Type getStrategyType();

    public abstract boolean isActive(Instant timestamp);
}
