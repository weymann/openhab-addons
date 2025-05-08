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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONObject;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link ForecastMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ForecastMock {

    public static final TimeZoneProvider TZP = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    public static final ZoneId TEST_ZONE = ZoneId.of("Europe/Berlin");
    public static final String PATTERN_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter dateInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static DateTimeFormatter dateOutputFormatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT)
            .withZone(ZoneId.systemDefault());

    public static TimeSeries getForecast() {
        TreeMap<ZonedDateTime, Double> wattMap = new TreeMap<>();
        String forecastString = FileReader.readFileInString("src/test/resources/forecastsolar/result.json");
        System.out.println(forecastString);
        JSONObject contentJson = new JSONObject(forecastString);
        JSONObject resultJson = contentJson.getJSONObject("result");
        JSONObject wattJson = resultJson.getJSONObject("watts");

        TimeSeries forecastSeries = new TimeSeries(Policy.REPLACE);
        dateOutputFormatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT).withZone(TZP.getTimeZone());
        Iterator<String> iter = wattJson.keys();
        while (iter.hasNext()) {
            String dateStr = iter.next();
            // convert date time into machine readable format
            ZonedDateTime zdt = LocalDateTime.parse(dateStr, dateInputFormatter).atZone(TZP.getTimeZone());
            wattMap.put(zdt, wattJson.getDouble(dateStr));
            forecastSeries.add(zdt.toInstant(), QuantityType.valueOf(wattJson.getDouble(dateStr), Units.WATT));
        }
        System.out.println(wattMap);
        return forecastSeries;
    }
}
