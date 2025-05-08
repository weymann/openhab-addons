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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;

/**
 * The {@link TibberMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TibberMock {

    public static final TimeZoneProvider TZP = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    public static final ZoneId TEST_ZONE = ZoneId.of("Europe/Berlin");
    public static final String PATTERN_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter dateInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static TimeSeries getPrices() {
        String priceString = FileReader.readFileInString("src/test/resources/tibber/price-query-response.json");
        JSONObject contentJson = new JSONObject(priceString);
        JSONObject resultJson = contentJson.getJSONObject("data").getJSONObject("viewer").getJSONObject("home")
                .getJSONObject("currentSubscription").getJSONObject("priceInfo");
        JSONArray today = resultJson.getJSONArray("today");
        JSONArray tomorrow = resultJson.getJSONArray("tomorrow");
        today.putAll(tomorrow);

        TimeSeries priceSeries = new TimeSeries(Policy.REPLACE);
        today.forEach(entry -> {
            String dateStr = ((JSONObject) entry).getString("startsAt");
            double price = ((JSONObject) entry).getDouble("total");
            priceSeries.add(Instant.parse(dateStr), QuantityType.valueOf(price + " DEF"));
        });
        return priceSeries;
    }
}
