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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The interface {@link Collision} marks a collision if a strategy .
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public abstract class Collision {
    /**
     * The enum {@link Type} defines the type of collision between strategies.
     * 
     */
    public enum Type {
        BATTERY_DISCHARGE_COLLISION,
        GRID_SUPPLY_COLLISION
    }
}
