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
package org.openhab.binding.curves.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link HouseholdMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class HouseholdMock {

    public static TimeSeries getConsumptionSeries() {
        Instant fixedInstant = Instant.parse("2022-07-17T00:00:00Z");
        Instant end = fixedInstant.plus(Duration.ofDays(2));
        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        while (fixedInstant.isBefore(end)) {
            Random r = new Random();
            int low = 564;
            int high = 987;
            int result = r.nextInt(high - low) + low;
            QuantityType<Power> qtp = QuantityType.valueOf(result, Units.WATT);
            ts.add(fixedInstant, qtp);
            fixedInstant = fixedInstant.plus(5, ChronoUnit.SECONDS);
        }
        return ts;
    }
}
