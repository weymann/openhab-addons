/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.tibber.internal;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static org.openhab.binding.tibber.internal.TibberBindingConstants.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.openhab.binding.tibber.internal.history.TibberHistory;
import org.openhab.binding.tibber.internal.history.TibberHistory.TimeWindow;
import org.openhab.binding.tibber.internal.history.TibberHistorySeries;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.unit.CurrencyUnits;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.test.storage.VolatileStorageService;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.internal.ThingImpl;
import org.openhab.core.types.TimeSeries;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link TestHistory} is testing Tibber history elements.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TestHistory {

    static TimeZoneProvider timeZoneProvider = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    void testHistory() {
        String fileName = "src/test/resources/daily-consumption-response.json";
        try {
            String content = new String(Files.readAllBytes(Paths.get(fileName)));
            JsonObject object = (JsonObject) JsonParser.parseString(content);
            JsonObject consumptionObject = Utils.getJsonObject(object, HISTORY_CONSUMPTION_JSON_PATH);
            JsonArray consumptionArray = consumptionObject.getAsJsonArray("edges");
            TibberHistorySeries series = new TibberHistorySeries(null);
            series.addData(consumptionArray);
            System.out.println("Consumption series size: " + series.toString());
            System.out.println("Consumption series start: " + series
                    .getTimeSeries(Instant.parse("2021-04-30T22:00:00Z"), TibberHistorySeries.PURPOSE_CONSUMPTION)
                    .size());
        } catch (IOException e) {
            fail("Error reading file " + fileName);
        }
    }

    @Test
    void testFullUpdate() {
        System.out.println(TimeWindow.ANNUAL.fullUpdate());
        System.out.println(Instant.now().minus(TimeWindow.ANNUAL.daysInWindow(), ChronoUnit.DAYS));
    }

    void testrealHistory() {
        CurrencyUnits.addUnit(CurrencyUnits.createCurrency("EUR", "€"));
        HttpClient httpClient = new HttpClient(new SslContextFactory.Client());
        StorageService storage = new VolatileStorageService();
        ThingImpl mockThing = new ThingImpl(TIBBER_THING_TYPE, "testThing");
        Bundle b = mock(Bundle.class);
        Utils.init(b);
        try {
            when(b.getVersion()).thenReturn(new Version(5, 1, 0));
            when(b.getResource(CONSUMPTION_QUERY))
                    .thenReturn(new File("src/main/resources" + CONSUMPTION_QUERY).toURI().toURL());
            when(b.getResource(PRODUCTION_QUERY))
                    .thenReturn(new File("src/main/resources" + PRODUCTION_QUERY).toURI().toURL());
        } catch (MalformedURLException e) {
            fail("Error reading resource files" + e.getMessage());
        }
        TibberHandlerMock handler = new TibberHandlerMock(mockThing, httpClient, mock(CronScheduler.class),
                timeZoneProvider, storage);
        handler.setCallback(mock(ThingHandlerCallback.class));
        handler.updateToken("TOKEN");
        handler.initialize();
        TibberHistory history = new TibberHistory(storage, "HOME_ID", handler);
        history.dispose(false);
        try {
            httpClient.start();
            history.updateHistory(TibberHistory.TimeWindow.ANNUAL.fullUpdate());
            Thread.sleep(15000);
            System.out.println("Terminated");
            // System.out.println(history.getHistory(TibberHistory.TimeWindow.ANNUAL));
        } catch (Exception e) {
            // e.printStackTrace();
        }
        Storage<String> store = storage.getStorage(TibberHistory.class.getName());
        store.getKeys().forEach(key -> {
            System.out.println("Key: " + key + ": " + store.get(key));
        });
        TibberHistorySeries series = history.getStoredSeries(TimeWindow.ANNUAL);
        System.out.println(series.toString());
        TimeSeries ts = series.getTimeSeries(Instant.MIN, TibberHistorySeries.PURPOSE_CONSUMPTION);
        ts.getStates().forEach(entry -> {
            System.out.println("Time: " + entry.timestamp() + " Value: " + entry.state().toFullString());
        });
        TimeSeries productionSeries = history.getStoredSeries(TimeWindow.ANNUAL).getTimeSeries(Instant.MIN,
                TibberHistorySeries.PURPOSE_PRODUCTION);
        System.out.println("Production series with size " + productionSeries.size());
    }
}
