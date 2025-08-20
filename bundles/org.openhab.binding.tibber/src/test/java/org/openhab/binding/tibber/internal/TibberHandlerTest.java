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
package org.openhab.binding.tibber.internal;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.tibber.internal.TibberBindingConstants.TIBBER_THING_TYPE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.openhab.binding.tibber.internal.handler.TibberHandler;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.scheduler.CronScheduler;
import org.openhab.core.thing.internal.ThingImpl;
import org.osgi.framework.BundleContext;

/**
 * The {@link TibberHandlerTest} checks handler initialization
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TibberHandlerTest {

    String readResponse(String fileName) {
        try {
            return new String(Files.readAllBytes(Paths.get(fileName)));
        } catch (IOException e) {
            fail("Error reading file " + fileName);
        }
        return "";
    }

    @Test
    void testSubsripction() {
        ContentResponse responseMock = mock(ContentResponse.class);
        when(responseMock.getStatus()).thenReturn(HttpStatus.OK_200);
        when(responseMock.getContentAsString())
                .thenReturn(readResponse("src/test/resources/subscription-null-response.json"));
        Request requestMock = mock(Request.class);
        when(requestMock.timeout(anyLong(), any())).thenReturn(requestMock);
        when(requestMock.header(any(HttpHeader.class), anyString())).thenReturn(requestMock);
        when(requestMock.header(anyString(), anyString())).thenReturn(requestMock);
        try {
            when(requestMock.send()).thenReturn(responseMock);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            fail(e.getMessage());
        }
        HttpClient httpClientMock = mock(HttpClient.class);
        when(httpClientMock.POST(anyString())).thenReturn(requestMock);
        ThingImpl thing = new ThingImpl(TIBBER_THING_TYPE, "test");
        Configuration config = new Configuration();
        config.put("token", "testToken");
        config.put("homeid", "testHomeId");
        thing.setConfiguration(config);

        TibberHandler handler = new TibberHandler(thing, httpClientMock, mock(CronScheduler.class),
                mock(BundleContext.class), mock(TimeZoneProvider.class));
        TibberHandlerCallbackMock callback = new TibberHandlerCallbackMock();
        handler.setCallback(callback);
        handler.initialize();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Waiting");
        }
        handler.dispose();
    }
}
