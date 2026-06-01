/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.brightsky.internal.api;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.brightsky.internal.dto.CurrentWeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * HTTP client for the BrightSky API ({@code https://api.brightsky.dev}).
 *
 * <p>
 * This is a plain class — not an OSGi component. It is instantiated by
 * {@link org.openhab.binding.brightsky.internal.handler.BrightSkyHandler}
 * using the shared {@link HttpClient} obtained from the framework's
 * {@code HttpClientFactory}. See ADR-002.
 *
 * <p>
 * Gson is configured with {@link FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}
 * to automatically map API {@code snake_case} fields to Java {@code camelCase}
 * DTO fields. See ADR-003.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BrightSkyApiClient {

    private static final String BASE_URL = "https://api.brightsky.dev";
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private final Logger logger = LoggerFactory.getLogger(BrightSkyApiClient.class);
    private final HttpClient httpClient;

    /**
     * Creates a new API client using the provided HTTP client.
     *
     * @param httpClient shared Jetty {@link HttpClient} from the openHAB framework
     * @throws IllegalArgumentException if {@code httpClient} is {@code null}
     */
    public BrightSkyApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches the current weather observation nearest to the given coordinates.
     *
     * @param lat geographic latitude (-90 to 90)
     * @param lon geographic longitude (-180 to 180)
     * @return parsed {@link CurrentWeatherResponse}
     * @throws BrightSkyApiException if the HTTP request fails or the response cannot be parsed
     */
    public CurrentWeatherResponse getCurrentWeather(double lat, double lon) throws BrightSkyApiException {
        String url = String.format("%s/current_weather?lat=%s&lon=%s", BASE_URL, lat, lon);
        return fetch(url, CurrentWeatherResponse.class);
    }

    /**
     * Fetches the current weather observation for a specific DWD station.
     *
     * @param stationId DWD station ID (e.g. {@code "00433"})
     * @return parsed {@link CurrentWeatherResponse}
     * @throws BrightSkyApiException if the HTTP request fails or the response cannot be parsed
     * @throws IllegalArgumentException if {@code stationId} is {@code null}
     */
    public CurrentWeatherResponse getCurrentWeatherByStation(String stationId) throws BrightSkyApiException {
        String url = String.format("%s/current_weather?dwd_station_id=%s", BASE_URL, stationId);
        return fetch(url, CurrentWeatherResponse.class);
    }

    /**
     * Executes an HTTP GET request and deserializes the JSON response body.
     *
     * @param <T> target type
     * @param url fully-qualified URL to fetch
     * @param responseClass class literal for Gson deserialization
     * @return deserialized response object
     * @throws BrightSkyApiException on HTTP error, timeout, or parse failure
     */
    private <T> T fetch(String url, Class<T> responseClass) throws BrightSkyApiException {
        logger.debug("GET {}", url);
        ContentResponse response;
        try {
            response = httpClient.newRequest(url).timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrightSkyApiException("Request interrupted: " + url, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new BrightSkyApiException("Request failed: " + url + " - " + e.getMessage(), e);
        }

        int statusCode = response.getStatus();
        if (!HttpStatus.isSuccess(statusCode)) {
            throw new BrightSkyApiException(String.format("HTTP %d from BrightSky API for URL: %s", statusCode, url));
        }

        String body = response.getContentAsString();
        logger.trace("Response body: {}", body);

        try {
            @Nullable
            T result = GSON.fromJson(body, responseClass);
            if (result == null) {
                throw new BrightSkyApiException("Empty response body from: " + url);
            }
            return result;
        } catch (JsonParseException e) {
            throw new BrightSkyApiException("Failed to parse response from: " + url + " - " + e.getMessage(), e);
        }
    }
}
