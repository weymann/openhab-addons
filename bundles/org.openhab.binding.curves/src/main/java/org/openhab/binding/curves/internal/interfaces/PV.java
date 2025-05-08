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

import java.time.Instant;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.State;

/**
 * The interface {@link PV} defines the functionality a PV production entity shall provide
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface PV extends CurvesEntity {
    public enum Type {
        FORECAST,
        INVERTER
    };

    public enum Unit {
        POWER,
        ENERGY
    };

    public TreeMap<Instant, State> getPredictionMap(Type type, Unit unit);
}
