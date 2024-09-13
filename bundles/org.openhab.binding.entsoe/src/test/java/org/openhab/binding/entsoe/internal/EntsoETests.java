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

package org.openhab.binding.entsoe.internal;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.API_TIMEOUT;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.THING_TYPE_SERVICE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.openhab.binding.entsoe.internal.exception.EntsoEResponseException;
import org.openhab.binding.entsoe.internal.exception.EntsoEUnexpectedException;
import org.openhab.binding.entsoe.internal.handler.EntsoEHandler;
import org.openhab.binding.entsoe.internal.utils.Client;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.unit.CurrencyUnits;
import org.openhab.core.thing.internal.ThingImpl;
import org.xml.sax.SAXException;

/**
 * {@link EntsoETests} testing updates in item metadata changes
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
class EntsoETests {
    private static TimeZoneProvider timeZoneProvider = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    @Test
    public void testHandler() {
        // mock thing with config
        ThingImpl thing = new ThingImpl(THING_TYPE_SERVICE, "4711");
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("securityToken", "1234567890");
        configMap.put("area", "10Y1001A1001A82H");
        configMap.put("historicDays", 1);
        Configuration config = new Configuration(configMap);
        thing.setConfiguration(config);

        String response = FileReader.readFileInString("src/test/resources/de-lu.xml");
        HttpUtilMock http = new HttpUtilMock();
        HttpClientFactory factoryMock = mock(HttpClientFactory.class);
        HttpClient clientMock = mock(HttpClient.class);
        http.setClientFactory(factoryMock);
        when(factoryMock.getCommonHttpClient()).thenReturn(clientMock);
        Request requestMock = mock(Request.class);
        when(clientMock.newRequest(anyString())).thenReturn(requestMock);
        when(requestMock.method(HttpMethod.GET)).thenReturn(requestMock);
        when(requestMock.timeout(API_TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(requestMock);
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getStatus()).thenReturn(200);
        when(responseMock.getContent()).thenReturn(response.getBytes());
        try {
            when(requestMock.send()).thenReturn(responseMock);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            fail();
        }

        // Create Handler with Callback
        ThingCallbackListener listener = new ThingCallbackListener();
        EntsoEHandler handler = new EntsoEHandler(thing, timeZoneProvider);
        handler.setCallback(listener);
        handler.initialize();
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
        }
    }

    @Test
    public void testResponse() {
        String response = FileReader.readFileInString("src/test/resources/de-lu.xml");
        try {
            Map<ZonedDateTime, Double> prices = Client.parseXmlResponse(response);
            System.out.println("Numbver of prices " + prices.size());
        } catch (ParserConfigurationException | SAXException | IOException | EntsoEResponseException
                | EntsoEUnexpectedException e) {
            // TODO Auto-generated catch block
        }
    }

    @Test
    public void testCurrencyConversion() {
        // CurrencyService currencyService = new CurrencyService();
        CurrencyUnits currencyUnits = new CurrencyUnits();
    }
}
