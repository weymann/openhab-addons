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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.TreeMap;

import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.curves.internal.calculator.BaseCurves;
import org.openhab.binding.curves.internal.calculator.CurveCalculationResult;
import org.openhab.binding.curves.internal.config.HemsConfiguration;
import org.openhab.binding.curves.internal.handler.ControllerMock;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link TestCurves} for curve generation use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestCurves {
    private static TimeZoneProvider timeZoneProvider = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    public static final ZoneId TEST_ZONE = ZoneId.of("Europe/Berlin");

    // @BeforeAll
    static void setFixedTimeJul17() {
        // Instant matching the date of test resources
        Instant fixedInstant = Instant.parse("2022-07-17T21:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        // TimeUtils.setClock(fixedClock);
    }

    static TimeSeries getConsumptionSeries() {
        Instant fixedInstant = Instant.parse("2022-07-17T00:00:00Z");
        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        int i = 0;
        while (i < Duration.ofMinutes(15).toSeconds()) {
            Random r = new Random();
            int low = 564;
            int high = 987;
            int result = r.nextInt(high - low) + low;
            QuantityType<Power> qtp = QuantityType.valueOf(result, Units.WATT);
            ts.add(fixedInstant.plus(i, ChronoUnit.SECONDS), qtp);
            i += 5;
        }
        return ts;
    }

    @Test
    void testSmallPowerCycles() {
        System.out.println("Test small power cycles");
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(15));
        Instant fixedInstant = Instant.parse("2022-07-17T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(15), times);

        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        int i = 0;
        while (i < Duration.ofMinutes(15).toSeconds()) {
            Random r = new Random();
            int low = 564;
            int high = 987;
            int result = r.nextInt(high - low) + low;
            QuantityType<Power> qtp = QuantityType.valueOf(result, Units.WATT);
            ts.add(fixedInstant.plus(i, ChronoUnit.SECONDS), qtp);
            i += 5;
        }

        System.out.println("Input Series:     " + ts.getBegin() + " - " + ts.getEnd());
        TreeMap<Instant, State> map = curves.getNormalizedCurve(ts, CalculationMethod.AVERAGE, true);
        System.out.println("Converted Map:    " + map.firstKey() + " - " + map.lastKey());
        TimeSeries convertedSeries = curves.toTimeSeries(map);
        System.out.println("Converted Series: " + convertedSeries.getBegin() + " - " + convertedSeries.getEnd());

        System.out.println("Input Series:    " + curves.toTreeMap(ts));
        System.out.println("Coverted Series: " + curves.toTreeMap(convertedSeries));
    }

    @Test
    void testFlatlineConversion() {
        System.out.println("testFlatline");
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(15));
        Instant fixedInstant = Instant.parse("2022-07-17T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(15), times);

        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        QuantityType<Power> qtpStart = QuantityType.valueOf(1234, Units.WATT);
        QuantityType<Power> qtpStop = QuantityType.valueOf(987, Units.WATT);
        ts.add(fixedInstant.minus(7, ChronoUnit.DAYS), qtpStart);
        ts.add(fixedInstant.plus(150, ChronoUnit.MINUTES), qtpStop);
        System.out.println("Input Series:     " + ts.getBegin() + " - " + ts.getEnd());
        TreeMap<Instant, State> map = curves.getNormalizedCurve(ts, CalculationMethod.AVERAGE, true);
        System.out.println("Converted Map:    " + map.firstKey() + " - " + map.lastKey());
        TimeSeries convertedSeries = curves.toTimeSeries(map);
        System.out.println("Converted Series: " + convertedSeries.getBegin() + " - " + convertedSeries.getEnd());

        System.out.println("Input Series:    " + curves.toTreeMap(ts));
        System.out.println("Coverted Series: " + curves.toTreeMap(convertedSeries));
    }

    @Test
    void testBaseCurves() {
        int duration = 1;
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(duration));
        Instant fixedInstant = Instant.parse("2022-07-17T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(duration), times);

        TimeSeries prices = TibberMock.getPrices();
        TimeSeries forecast = ForecastMock.getForecast();
        TimeSeries consumption = HouseholdMock.getConsumptionSeries();

        TreeMap<Instant, State> forecastMap = curves.getNormalizedCurve(forecast, CalculationMethod.INTERPOLATE, true);
        TreeMap<Instant, State> consumptionMap = curves.getNormalizedCurve(consumption, CalculationMethod.AVERAGE,
                true);
        TreeMap<Instant, State> priceMap = curves.getNormalizedCurve(prices, CalculationMethod.NONE, true);

        HemsBridge controller = ControllerMock.createController();
        BaseCurves baseCurves = new BaseCurves(controller.datalake());
        baseCurves.addForecast(forecastMap);
        baseCurves.addHousehold(consumptionMap);
        baseCurves.setPrices(priceMap);

        HemsConfiguration config = new HemsConfiguration();
        System.out.println("Granularity: " + controller.getGranularity());
        CurveCalculationResult result = new CurveCalculationResult(baseCurves, controller);
        // result.calculateCurves(List.of());
        // List<ScheduleEntry> schedule = result.getSchedule();
        // System.out.println("Schedule size: " + schedule.size());
        // schedule.forEach(entry -> {
        // // System.out.println("Entry: " + entry.toString());
        // });
    }
}
