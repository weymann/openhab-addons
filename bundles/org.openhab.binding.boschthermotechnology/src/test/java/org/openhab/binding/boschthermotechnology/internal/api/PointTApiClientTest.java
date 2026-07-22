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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;

import com.google.gson.Gson;

/**
 * Unit tests for {@link PointTApiClient}, covering the mandatory API-client edge-case checklist
 * from {@code rules/testing-rules.md} for the methods added/changed in ADR-005/ADR-006:
 * {@link PointTApiClient#tryGetResource}, {@link PointTApiClient#listResourceIds}, and the
 * {@code httpStatus}-carrying behavior of the private {@code send(...)} method (exercised
 * indirectly through {@link PointTApiClient#getResource}).
 *
 * @author Bernd Weymann - Initial contribution
 */
class PointTApiClientTest {

    private static final String ACCESS_TOKEN = "test-token";
    private static final String GATEWAY_ID = "gw1";
    private static final String PATH = "someResource";

    private HttpClient httpClient = mock(HttpClient.class);
    private Request request = mock(Request.class);
    private ContentResponse response = mock(ContentResponse.class);
    private PointTApiClient client = new PointTApiClient(httpClient, new Gson());

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        request = mock(Request.class);
        response = mock(ContentResponse.class);
        client = new PointTApiClient(httpClient, new Gson());

        when(httpClient.newRequest(anyString())).thenReturn(request);
        when(request.method(any(HttpMethod.class))).thenReturn(request);
        when(request.header(any(org.eclipse.jetty.http.HttpHeader.class), anyString())).thenReturn(request);
        when(request.timeout(anyLong(), any(TimeUnit.class))).thenReturn(request);
        when(request.content(any())).thenReturn(request);
        when(request.getURI()).thenReturn(URI.create("https://pointt-api.example/test"));
        when(request.getMethod()).thenReturn("GET");
    }

    private void stubResponse(int status, String body) throws Exception {
        when(response.getStatus()).thenReturn(status);
        when(response.getContentAsString()).thenReturn(body);
        when(request.send()).thenReturn(response);
    }

    // --- happy path ---

    @Test
    void whenResourceResponseIsValid_thenGetResourceReturnsParsedDto() throws Exception {
        // Arrange
        stubResponse(200, "{\"id\":\"someResource\",\"type\":\"Float\",\"writeable\":0,\"value\":21.5}");

        // Act
        ResourceDto resource = client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH);

        // Assert
        assertEquals("someResource", resource.id);
        assertEquals(21.5, resource.value.getAsDouble());
    }

    @Test
    void whenListResourceIdsResponseIsValid_thenIdsAreExtractedInOrder() throws Exception {
        // Arrange
        stubResponse(200, "[{\"id\":\"hc1\"},{\"id\":\"hc2\"}]");

        // Act
        List<String> ids = client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "heatingCircuits");

        // Assert
        assertEquals(List.of("hc1", "hc2"), ids);
    }

    // --- empty response ---

    @Test
    void whenListResourceIdsResponseIsEmptyArray_thenReturnsEmptyList() throws Exception {
        // Arrange
        stubResponse(200, "[]");

        // Act
        List<String> ids = client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "heatingCircuits");

        // Assert
        assertTrue(ids.isEmpty());
    }

    @Test
    void whenListResourceIdsEntriesHaveBlankOrNullId_thenBlankEntriesAreFilteredOut() throws Exception {
        // Arrange
        stubResponse(200, "[{\"id\":\"hc1\"},{\"id\":\"\"},{}]");

        // Act
        List<String> ids = client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "heatingCircuits");

        // Assert
        assertEquals(List.of("hc1"), ids);
    }

    @Test
    void whenGetResourceResponseIsEmptyBody_thenThrowsApiException() throws Exception {
        // Arrange
        stubResponse(200, "");

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    // --- HTTP error responses ---

    @Test
    void whenGetResourceReceives401_thenThrowsAuthException() throws Exception {
        // Arrange
        stubResponse(HttpStatus.UNAUTHORIZED_401, "");

        // Act + Assert
        assertThrows(PointTAuthException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    @Test
    void whenGetResourceReceives500_thenThrowsApiExceptionCarryingHttpStatus() throws Exception {
        // Arrange
        stubResponse(HttpStatus.INTERNAL_SERVER_ERROR_500, "");

        // Act
        PointTApiException thrown = assertThrows(PointTApiException.class,
                () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, thrown.getHttpStatus());
    }

    @Test
    void whenTryGetResourceReceives404_thenReturnsEmptyOptionalInsteadOfThrowing() throws Exception {
        // Arrange
        stubResponse(HttpStatus.NOT_FOUND_404, "");

        // Act
        Optional<ResourceDto> result = client.tryGetResource(ACCESS_TOKEN, GATEWAY_ID, PATH);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void whenTryGetResourceReceives500_thenStillThrowsApiException() throws Exception {
        // Arrange
        stubResponse(HttpStatus.INTERNAL_SERVER_ERROR_500, "");

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.tryGetResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    @Test
    void whenListResourceIdsReceives404_thenReturnsEmptyListInsteadOfThrowing() throws Exception {
        // Arrange
        stubResponse(HttpStatus.NOT_FOUND_404, "");

        // Act
        List<String> ids = client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "solarCircuits");

        // Assert
        assertTrue(ids.isEmpty());
    }

    @Test
    void whenListResourceIdsReceives500_thenStillThrowsApiException() throws Exception {
        // Arrange
        stubResponse(HttpStatus.INTERNAL_SERVER_ERROR_500, "");

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "solarCircuits"));
    }

    // --- malformed JSON ---

    @Test
    void whenGetResourceReceivesMalformedJson_thenThrowsApiExceptionNotUncaughtParseError() throws Exception {
        // Arrange
        stubResponse(200, "{not valid json");

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    @Test
    void whenListResourceIdsReceivesMalformedJson_thenThrowsApiExceptionNotUncaughtParseError() throws Exception {
        // Arrange
        stubResponse(200, "not an array");

        // Act + Assert
        assertThrows(PointTApiException.class,
                () -> client.listResourceIds(ACCESS_TOKEN, GATEWAY_ID, "heatingCircuits"));
    }

    // --- timeout / connection failure ---

    @Test
    void whenRequestTimesOut_thenThrowsApiExceptionNotUncaughtTimeoutException() throws Exception {
        // Arrange
        when(request.send()).thenThrow(new TimeoutException("no response"));

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    @Test
    void whenConnectionFails_thenThrowsApiExceptionNotUncaughtExecutionException() throws Exception {
        // Arrange
        when(request.send()).thenThrow(new ExecutionException("connection refused", new java.io.IOException()));

        // Act + Assert
        assertThrows(PointTApiException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));
    }

    @Test
    void whenRequestIsInterrupted_thenThreadInterruptFlagIsRestored() throws Exception {
        // Arrange
        when(request.send()).thenThrow(new InterruptedException("interrupted"));

        // Act
        assertThrows(PointTApiException.class, () -> client.getResource(ACCESS_TOKEN, GATEWAY_ID, PATH));

        // Assert
        assertTrue(Thread.interrupted(), "Thread.interrupt() must be restored after swallowing InterruptedException");
    }
}
