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
package org.openhab.binding.boschthermotechnology.internal.api;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.POINTT_API_BASE_URL;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschthermotechnology.internal.dto.GatewayDto;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link PointTApiClient} talks to the Bosch Thermotechnology PointT REST API
 * ({@code pointt-api.bosch-thermotechnology.com}). It treats the API generically as a
 * resource-path tree, matching the design of the reference implementation analyzed in
 * {@code buderus-reverse.md}.
 *
 * <p>
 * The access token is passed in per call rather than held as state here, because token
 * lifecycle (refresh, persistence) is owned by {@code AccountBridgeHandler} via openHAB core's
 * {@code OAuthClientService} - this class has a single responsibility: turn resource paths into
 * HTTP calls.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PointTApiClient {

    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(PointTApiClient.class);
    private final HttpClient httpClient;
    private final Gson gson;

    public PointTApiClient(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Lists all gateways associated with the account behind the given access token.
     *
     * @throws PointTAuthException if the token is rejected (HTTP 401/403)
     * @throws PointTApiException on any other communication or parsing failure
     */
    public List<GatewayDto> listGateways(String accessToken) throws PointTApiException {
        String body = get(accessToken, POINTT_API_BASE_URL + "/gateways/");
        try {
            List<GatewayDto> gateways = gson.fromJson(body, new TypeToken<List<GatewayDto>>() {
            }.getType());
            return gateways == null ? List.of() : List.copyOf(gateways);
        } catch (JsonSyntaxException e) {
            throw new PointTApiException("Could not parse gateway list response", e);
        }
    }

    /**
     * Reads a single resource of a gateway.
     *
     * @throws PointTAuthException if the token is rejected (HTTP 401/403)
     * @throws PointTApiException on any other communication or parsing failure
     */
    public ResourceDto getResource(String accessToken, String gatewayId, String path) throws PointTApiException {
        String body = get(accessToken, resourceUrl(gatewayId, path));
        try {
            ResourceDto resource = gson.fromJson(body, ResourceDto.class);
            if (resource == null) {
                throw new PointTApiException("Empty resource response for path " + path);
            }
            return resource;
        } catch (JsonSyntaxException e) {
            throw new PointTApiException("Could not parse resource response for path " + path, e);
        }
    }

    /**
     * Writes a single resource of a gateway. Mirrors {@code BuderusPointTClient.set_resource_value()}:
     * first attempts the short {@code {"value": ...}} payload, and falls back to sending the full
     * resource object ({@code id}, {@code type}, {@code writeable}, {@code value}) if that is rejected.
     *
     * @throws PointTAuthException if the token is rejected (HTTP 401/403)
     * @throws PointTApiException on any other communication or parsing failure, including when both
     *             payload variants are rejected
     */
    public void putResource(String accessToken, String gatewayId, String path, JsonElement value)
            throws PointTApiException {
        String url = resourceUrl(gatewayId, path);
        String shortPayload = gson.toJson(new ShortValuePayload(value));
        try {
            put(accessToken, url, shortPayload);
            return;
        } catch (PointTAuthException e) {
            throw e;
        } catch (PointTApiException e) {
            logger.debug("Short value payload rejected for {}, retrying with full resource object: {}", path,
                    e.getMessage());
        }

        ResourceDto currentResource = getResource(accessToken, gatewayId, path);
        currentResource.value = value;
        String fullPayload = gson.toJson(currentResource);
        put(accessToken, url, fullPayload);
    }

    private String resourceUrl(String gatewayId, String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return POINTT_API_BASE_URL + "/gateways/" + gatewayId + "/resource/" + normalizedPath;
    }

    private String get(String accessToken, String url) throws PointTApiException {
        logger.trace("--> GET {}", url);
        Request request = httpClient.newRequest(url).method(HttpMethod.GET)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken)
                .timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return send(request);
    }

    private void put(String accessToken, String url, String jsonBody) throws PointTApiException {
        logger.trace("--> PUT {} body={}", url, jsonBody);
        Request request = httpClient.newRequest(url).method(HttpMethod.PUT)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken)
                .content(new StringContentProvider("application/json", jsonBody,
                        java.nio.charset.StandardCharsets.UTF_8))
                .timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        send(request);
    }

    /**
     * Sends the request and returns the response body. Every call is logged at TRACE level - both
     * the outgoing request (method + URL, and body for writes) in {@link #get} / {@link #put}, and
     * the resulting status/body here - deliberately verbose for first-version debugging of resource
     * paths and value shapes against real gateways (see the TODOs on several channel types in
     * {@code thing-types.xml}). The {@code Authorization} header itself is never logged.
     */
    private String send(Request request) throws PointTApiException {
        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PointTApiException("Request to " + request.getURI() + " was interrupted", e);
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
            throw new PointTApiException("Request to " + request.getURI() + " failed", e);
        }

        int status = response.getStatus();
        String body = response.getContentAsString();
        logger.trace("<-- {} {} HTTP {} body={}", request.getMethod(), request.getURI(), status, body);

        if (status == HttpStatus.UNAUTHORIZED_401 || status == HttpStatus.FORBIDDEN_403) {
            throw new PointTAuthException("PointT API rejected the access token (HTTP " + status + ")");
        }
        if (!HttpStatus.isSuccess(status)) {
            throw new PointTApiException("PointT API request to " + request.getURI() + " failed with HTTP " + status);
        }
        return body;
    }

    /**
     * Minimal payload shape for the first write attempt: {@code {"value": ...}}. Not a public API
     * type, DTO-style, exempt from {@code @NonNullByDefault}.
     */
    private static final class ShortValuePayload {
        @SuppressWarnings("unused")
        private final JsonElement value;

        ShortValuePayload(JsonElement value) {
            this.value = value;
        }
    }
}
