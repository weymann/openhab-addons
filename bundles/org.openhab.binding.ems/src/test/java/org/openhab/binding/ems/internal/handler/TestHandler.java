/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.ems.internal.handler;

import static org.mockito.Mockito.*;

import java.util.Calendar;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.ems.internal.utils.FileReader;
import org.openhab.core.library.types.PointType;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.osgi.framework.BundleContext;

/**
 * The {@link TestHandler} Test several formulas used in binding
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestHandler {

    @Test
    public void testEMSHandler() {
        PointType pt = PointType.valueOf("50.0,8.0");
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("testbinding", "test"));
        EMSHandler ems = new EMSHandler(thing, pt, mock(BundleContext.class));

        String forecast = FileReader.readFileInString("src/test/resources/weatherforecast.json");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis((long) 1620509255 * 1000);

        ems.updatePrediction(c, new WeatherForecast(forecast));
    }
}
