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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * The {@link TestMerge} for generic use cases
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class TestMerge {

    @Test
    void testMerge() {
        String file1 = "src/test/resources/forecastsolar/forecast-response-1.json";
        String file2 = "src/test/resources/forecastsolar/forecast-response-2.json";
        try {
            String content1 = new String(Files.readAllBytes(Paths.get(file1)));
            String content2 = new String(Files.readAllBytes(Paths.get(file2)));
            JSONObject json1 = new JSONObject(content1);
            JSONObject json2 = new JSONObject(content2);
            JSONObject watts1 = json1.getJSONObject("result").getJSONObject("watts");
            JSONObject watts2 = json2.getJSONObject("result").getJSONObject("watts");
            JSONObject mergedResult = new JSONObject();
            JSONObject mergedWatts = new JSONObject();
            watts1.keySet().forEach(key -> {
                int watts = watts1.getInt(key) + watts2.getInt(key);
                mergedWatts.put(key, watts);
            });
            // JSONObject intermediateWatts = new JSONObject(mergedWatts);
            // System.out.println("Merged Watts: " + mergedWatts);
            mergedResult.put("watts", mergedWatts);

            JSONObject periods1 = json1.getJSONObject("result").getJSONObject("watt_hours_period");
            JSONObject periods2 = json2.getJSONObject("result").getJSONObject("watt_hours_period");
            TreeMap<String, Integer> mergedPeriodsMap = new TreeMap<>();
            periods1.keySet().forEach(key -> {
                int period = periods1.getInt(key) + periods2.getInt(key);
                mergedPeriodsMap.put(key, period);
            });
            mergedResult.put("watt_hours_period", mergedPeriodsMap);

            JSONObject wattHours1 = json1.getJSONObject("result").getJSONObject("watt_hours");
            JSONObject wattHours2 = json2.getJSONObject("result").getJSONObject("watt_hours");
            TreeMap<String, Integer> mergedWattHoursMap = new TreeMap<>();
            wattHours1.keySet().forEach(key -> {
                int wattHour = wattHours1.getInt(key) + wattHours2.getInt(key);
                mergedWattHoursMap.put(key, wattHour);
            });
            mergedResult.put("watt_hours", mergedWattHoursMap);

            System.out.println("Merged Result: " + mergedResult.toString(2));
        } catch (IOException e) {
            fail();
        }
    }
}
