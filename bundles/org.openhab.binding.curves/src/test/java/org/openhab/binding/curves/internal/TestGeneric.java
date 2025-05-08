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

import static org.mockito.Mockito.mock;
import static org.openhab.binding.curves.internal.CurvesConstants.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.handler.PVHandler;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.internal.BridgeImpl;
import org.openhab.core.thing.internal.ThingImpl;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link TestGeneric} for generic use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestGeneric {
    private static TimeZoneProvider timeZoneProvider = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    public static final ZoneId TEST_ZONE = ZoneId.of("Europe/Berlin");
    private static final double TOLERANCE = 0.001;

    // @BeforeAll
    static void setFixedTimeJul17() {
        // Instant matching the date of test resources
        Instant fixedInstant = Instant.parse("2022-07-17T21:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        // Utils.setClock(fixedClock);
    }

    void test() {
        BridgeImpl bi = new BridgeImpl(THING_TYPE_HEMS, "curves-bridge");
        HemsBridge controller = new HemsBridge(bi, mock(ItemRegistry.class), mock(PersistenceServiceRegistry.class),
                mock(TimeZoneProvider.class));
        ThingImpl ti = new ThingImpl(THING_TYPE_PV, "pv");
        PVHandler pvH = new PVHandler(ti);
        controller.registerEntity(pvH);
    }

    void testStartTime() {
        TimeUtils timeUtils = new TimeUtils(timeZoneProvider, Duration.of(15, ChronoUnit.MINUTES));
        System.out.println(Instant.now());
        System.out.println(timeUtils.timeFrame(Instant.now(), 0));
        System.out
                .println(Instant.now().atZone(timeZoneProvider.getTimeZone()).withMinute(0).withSecond(0).toInstant());
        // assertTrue(timeUtils.getStartPoint(Instant.now(), Duration.of(15, ChronoUnit.MINUTES))
        // .equals(Instant.now().atZone(timeZoneProvider.getTimeZone()).withMinute(0).withSecond(0).toInstant()));
    }

    void testEnergyCurveConversion() {
        System.out.println("EnergyTest");
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(60));
        Instant fixedInstant = Instant.parse("2022-07-17T09:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(60), times);

        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        QuantityType<Energy> qtp = QuantityType.valueOf(3, Units.WATT_HOUR);
        ts.add(fixedInstant, qtp);
        ts.add(fixedInstant.plus(150, ChronoUnit.MINUTES), qtp);
        System.out.println("Input Series:     " + ts.getBegin() + " - " + ts.getEnd());
        TreeMap<Instant, State> map = curves.getNormalizedCurve(ts, CalculationMethod.AVERAGE, true);
        System.out.println("Converted Map:    " + map.firstKey() + " - " + map.lastKey());
        TimeSeries convertedSeries = curves.toTimeSeries(map);
        System.out.println("Converted Series: " + convertedSeries.getBegin() + " - " + convertedSeries.getEnd());

        System.out.println("Input Series:    " + curves.toTreeMap(ts));
        System.out.println("Coverted Series: " + curves.toTreeMap(convertedSeries));
    }

    @Test
    void testDayConversion() {
        System.out.println("Day Test");
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(60));
        Instant fixedInstant = Instant.parse("2022-07-17T17:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(5), times);

        TimeSeries ts = new TimeSeries(Policy.REPLACE);
        QuantityType<Power> qtp = QuantityType.valueOf(500, Units.WATT);
        Instant fixedStart = Instant.parse("2022-07-17T00:00:00Z");
        for (int i = 0; i < 24; i++) {
            ts.add(fixedStart, qtp);
            fixedStart = fixedStart.plus(60, ChronoUnit.MINUTES);
        }

        System.out.println("Input Series:     " + ts.getBegin() + " - " + ts.getEnd());
        TreeMap<Instant, State> map = curves.getNormalizedCurve(ts, CalculationMethod.AVERAGE, true);
        System.out.println("Converted Map:    " + map.firstKey() + " - " + map.lastKey());
        TimeSeries convertedSeries = curves.toTimeSeries(map);
        System.out.println("Converted Series: " + convertedSeries.getBegin() + " - " + convertedSeries.getEnd());

        System.out.println("Input Series:    " + curves.toTreeMap(ts));
        System.out.println("Coverted Series: " + curves.toTreeMap(convertedSeries));
    }

    void testDaysAhead() {
        TimeUtils times = new TimeUtils(timeZoneProvider, Duration.ofMinutes(60));
        Instant fixedInstant = Instant.parse("2022-07-17T09:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, TEST_ZONE);
        times.setClock(fixedClock);
        System.out.println("Ahead +" + times.startOfDay(5));
        System.out.println("Ahead -" + times.startOfDay(-5));
    }

    void testQuantityType() {
        QuantityType p1 = QuantityType.valueOf("5 W");
        QuantityType p2 = QuantityType.valueOf("3 W");
        System.out.println("QUNATITYTPE " + p1.add(p2));
    }
}
