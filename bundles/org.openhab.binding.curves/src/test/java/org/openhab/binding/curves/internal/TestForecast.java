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
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;

/**
 * The {@link TestForecast} for curve generation use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestForecast {

    @Test
    void testForecastConversions() {
        TimeSeries forecastSeries = ForecastMock.getForecast();
        System.out.println("Series: " + forecastSeries.getBegin() + " : " + forecastSeries.getEnd());
        TimeUtils times = new TimeUtils(ForecastMock.TZP, Duration.ofMinutes(60));
        CurveUtils curves = new CurveUtils(Duration.ofMinutes(60), times);
        Instant fixedInstant = Instant.parse("2022-07-17T02:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ForecastMock.TEST_ZONE);
        times.setClock(fixedClock);

        forecastSeries.add(times.startOfDay(0), QuantityType.valueOf("0 W"));
        forecastSeries.add(times.startOfDay(2), QuantityType.valueOf("0 W"));
        System.out.println("Series: " + forecastSeries.getBegin() + " : " + forecastSeries.getEnd());

        TreeMap<Instant, State> forecastMap = curves.getNormalizedCurve(forecastSeries, CalculationMethod.AVERAGE,
                true);
        System.out.println("Map 60: " + forecastMap.firstKey() + " : " + forecastMap.lastKey());
        System.out.println(forecastMap);

        // assertEquals(forecastSeries.size(), forecastMap.size(), "1hr same size");
        // assertEquals(forecastSeries.getBegin(), forecastMap.firstKey(), "Same Begin");
        // assertEquals(forecastSeries.getEnd(), forecastMap.lastKey(), "Same End");
        // forecastSeries.getStates().forEach(state -> {
        // Instant timestamp = state.timestamp();
        // assertEquals(state.state(), forecastMap.floorEntry(timestamp).getValue(), "Same value");
        // });

        // curves = new CurveUtils(Duration.ofMinutes(30), times);
        // forecastMap = curves.getNormalizedCurve(forecastSeries, CalculationMethod.AVERAGE, false);
        // System.out.println("Map 30: " + forecastMap.firstKey() + " : " + forecastMap.lastKey());
        // System.out.println(forecastMap);
        //
        // curves = new CurveUtils(Duration.ofMinutes(15), times);
        // forecastMap = curves.getNormalizedCurve(forecastSeries, CalculationMethod.AVERAGE, false);
        // System.out.println("Map 15: " + forecastMap.firstKey() + " : " + forecastMap.lastKey());
        // System.out.println(forecastMap);
    }
    // public void readForecast(String id, String content, Instant expirationDate) {
    // expirationDateTime = expirationDate;
    // identifier = id;
    // if (!content.isEmpty()) {
    // rawData = Optional.of(content);
    // try {
    // JSONObject contentJson = new JSONObject(content);
    // JSONObject resultJson = contentJson.getJSONObject("result");
    // JSONObject wattHourJson = resultJson.getJSONObject("watt_hours");
    // JSONObject wattJson = resultJson.getJSONObject("watts");
    // String zoneStr = contentJson.getJSONObject("message").getJSONObject("info").getString("timezone");
    // zone = ZoneId.of(zoneStr);
    // dateOutputFormatter = DateTimeFormatter.ofPattern(SolarForecastBindingConstants.PATTERN_FORMAT)
    // .withZone(zone);
    // Iterator<String> iter = wattHourJson.keys();
    // // put all values of the current day into sorted tree map
    // while (iter.hasNext()) {
    // String dateStr = iter.next();
    // // convert date time into machine readable format
    // try {
    // ZonedDateTime zdt = LocalDateTime.parse(dateStr, dateInputFormatter).atZone(zone);
    // wattHourMap.put(zdt, wattHourJson.getDouble(dateStr));
    // wattMap.put(zdt, wattJson.getDouble(dateStr));
    // } catch (DateTimeParseException dtpe) {
    // logger.warn("Error parsing time {} Reason: {}", dateStr, dtpe.getMessage());
    // throw new SolarForecastException(this,
    // "Error parsing time " + dateStr + " Reason: " + dtpe.getMessage());
    // }
    // }
    // } catch (JSONException je) {
    // throw new SolarForecastException(this,
    // "Error parsing JSON response " + content + " Reason: " + je.getMessage());
    // }
    // }
    // }

    // public TimeSeries getEnergyTimeSeries(QueryMode mode) {
    // TimeSeries ts = new TimeSeries(Policy.REPLACE);
    // Instant now = Instant.now(Utils.getClock());
    // wattHourMap.forEach((timestamp, energy) -> {
    // Instant entryTimestamp = timestamp.toInstant();
    // if (Utils.isAfterOrEqual(entryTimestamp, now)) {
    // ts.add(entryTimestamp, Utils.getEnergyState(energy / 1000.0));
    // }
    // });
    // return ts;
    // }
}
