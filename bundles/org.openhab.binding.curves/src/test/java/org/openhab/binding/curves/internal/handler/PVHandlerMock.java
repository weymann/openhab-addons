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
package org.openhab.binding.curves.internal.handler;

import static org.openhab.binding.curves.internal.CurvesConstants.THING_TYPE_PV;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.PVConfiguration;
import org.openhab.core.thing.internal.ThingImpl;

/**
 * The {@link PVHandlerMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PVHandlerMock extends PVHandler {
    private static final String POWER_FORECAST_ITEM = "forecast-item-test";

    public PVHandlerMock() {
        super(new ThingImpl(THING_TYPE_PV, "test-pv"));
        config = new PVConfiguration();
        config.powerForecastItem = POWER_FORECAST_ITEM;
    }
}
