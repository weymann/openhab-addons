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
import org.openhab.core.types.TimeSeries;

/**
 * The interface {@link Grid} defines the functionality a PV production entity shall provide
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface Grid extends CurvesEntity {

    public void sendGridConsumptionTimeSeries(TimeSeries consumption);

    public void sendGridSupplyTimeSeries(TimeSeries supply);

    public TreeMap<Instant, State> getPriceMap();
}
