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
package org.openhab.binding.brightsky.internal.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.brightsky.internal.BrightSkyBindingConstants.*;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.brightsky.internal.api.BrightSkyApiClient;
import org.openhab.binding.brightsky.internal.dto.CurrentWeatherResponse;
import org.openhab.binding.brightsky.internal.dto.Source;
import org.openhab.binding.brightsky.internal.dto.WeatherRecord;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * Unit test for {@link BrightSkyHandler}.
 *
 * <p>
 * Uses a real JSON response captured from {@code GET /current_weather?lat=52.52&lon=13.41}
 * on 2026-05-28 to verify that every channel is updated with the correct value and unit.
 * The HTTP layer is replaced by a Mockito stub so no network access is required.
 *
 * <p>
 * Real response used as test fixture:
 *
 * <pre>
 * {
 *   "weather": {
 *     "source_id": 303711,
 *     "timestamp": "2026-05-28T08:00:00+00:00",
 *     "cloud_cover": 38,
 *     "condition": "dry",
 *     "dew_point": 6.54,
 *     "solar_30": 0.329,
 *     "precipitation_10": 0.0,
 *     "pressure_msl": 1025.1,
 *     "relative_humidity": 53,
 *     "visibility": 33886,
 *     "wind_direction_10": 300,
 *     "wind_speed_10": 13.7,
 *     "wind_gust_direction_10": 310,
 *     "wind_gust_speed_10": 22.0,
 *     "sunshine_30": 30.0,
 *     "temperature": 16.1,
 *     "icon": "partly-cloudy-day"
 *   }
 * }
 * </pre>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrightSkyHandlerTest {

    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_WEATHER_LOCATION, "test");

    // Real values from live API response captured 2026-05-28
    private static final String TIMESTAMP = "2026-05-28T08:00:00+00:00";
    private static final double TEMPERATURE = 16.1;
    private static final double DEW_POINT = 6.54;
    private static final int HUMIDITY = 53;
    private static final double PRESSURE_HPA = 1025.1;
    private static final double WIND_SPEED = 13.7;
    private static final int WIND_DIRECTION = 300;
    private static final double WIND_GUST_SPEED = 22.0;
    private static final int WIND_GUST_DIRECTION = 310;
    private static final double PRECIPITATION = 0.0;
    private static final int CLOUD_COVER = 38;
    private static final int VISIBILITY = 33886;
    private static final double SUNSHINE = 30.0;
    private static final double SOLAR = 0.329;
    private static final String CONDITION = "dry";
    private static final String ICON = "partly-cloudy-day";

    @Mock
    @NonNullByDefault({})
    private Thing thing;

    @Mock
    @NonNullByDefault({})
    private ThingHandlerCallback callback;

    @Mock
    @NonNullByDefault({})
    private HttpClientFactory httpClientFactory;

    @Mock
    @NonNullByDefault({})
    private HttpClient httpClient;

    @Mock
    @NonNullByDefault({})
    private BrightSkyApiClient apiClient;

    @NonNullByDefault({})
    private BrightSkyHandler handler;

    @BeforeEach
    void setUp() {
        when(thing.getUID()).thenReturn(THING_UID);
        when(thing.getChannels()).thenReturn(List.of());
        when(thing.getConfiguration()).thenReturn(new Configuration(
                Map.of(CONFIG_LATITUDE, 52.52, CONFIG_LONGITUDE, 13.41, CONFIG_REFRESH_INTERVAL, 30)));
        when(httpClientFactory.getCommonHttpClient()).thenReturn(httpClient);

        handler = new BrightSkyHandler(thing, httpClientFactory) {
            @Override
            protected BrightSkyApiClient createApiClient(HttpClient client) {
                return apiClient;
            }
        };
        handler.setCallback(callback);
    }

    /**
     * Happy path: a complete, valid API response must update all 16 channels
     * with correctly converted values and set the Thing status to ONLINE.
     */
    @Test
    void happyPathAllChannelsUpdatedCorrectly() throws Exception {
        when(apiClient.getCurrentWeather(52.52, 13.41)).thenReturn(buildResponse());

        handler.initialize();
        handler.poll();

        ArgumentCaptor<ChannelUID> channelCaptor = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(callback, atLeastOnce()).stateUpdated(channelCaptor.capture(), stateCaptor.capture());

        Map<String, State> states = buildStateMap(channelCaptor.getAllValues(), stateCaptor.getAllValues());

        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_TEMPERATURE, TEMPERATURE, SIUnits.CELSIUS);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_DEW_POINT, DEW_POINT, SIUnits.CELSIUS);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_HUMIDITY, HUMIDITY, Units.PERCENT);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_PRESSURE, PRESSURE_HPA * 100.0, SIUnits.PASCAL);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_WIND_SPEED, WIND_SPEED, SIUnits.KILOMETRE_PER_HOUR);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_WIND_DIRECTION, WIND_DIRECTION, Units.DEGREE_ANGLE);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_SPEED, WIND_GUST_SPEED,
                SIUnits.KILOMETRE_PER_HOUR);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_DIRECTION, WIND_GUST_DIRECTION,
                Units.DEGREE_ANGLE);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_PRECIPITATION, PRECIPITATION * 0.001, SIUnits.METRE);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_CLOUD_COVER, CLOUD_COVER, Units.PERCENT);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_VISIBILITY, VISIBILITY, SIUnits.METRE);
        assertQuantity(states, GROUP_CURRENT + "#" + CHANNEL_SUNSHINE, SUNSHINE, Units.MINUTE);
        assertStringState(states, GROUP_CURRENT + "#" + CHANNEL_CONDITION, CONDITION);
        assertStringState(states, GROUP_CURRENT + "#" + CHANNEL_ICON, ICON);

        State timeState = states.get(GROUP_CURRENT + "#" + CHANNEL_OBSERVATION_TIME);
        assert timeState instanceof DateTimeType : "Expected DateTimeType but got: " + timeState;

        verify(callback, atLeastOnce()).statusUpdated(eq(thing),
                argThat(status -> status.getStatus() == ThingStatus.ONLINE));
    }

    /**
     * Null fields in the API response must map to UnDefType.UNDEF - never skip updateState.
     */
    @Test
    void nullFieldsMapToUndef() throws Exception {
        CurrentWeatherResponse response = new CurrentWeatherResponse();
        response.weather = new WeatherRecord();
        response.sources = List.of();

        when(apiClient.getCurrentWeather(52.52, 13.41)).thenReturn(response);

        handler.initialize();
        handler.poll();

        ArgumentCaptor<ChannelUID> channelCaptor = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> stateCaptor = ArgumentCaptor.forClass(State.class);
        verify(callback, atLeastOnce()).stateUpdated(channelCaptor.capture(), stateCaptor.capture());

        Map<String, State> states = buildStateMap(channelCaptor.getAllValues(), stateCaptor.getAllValues());

        for (String channelId : List.of(GROUP_CURRENT + "#" + CHANNEL_TEMPERATURE,
                GROUP_CURRENT + "#" + CHANNEL_DEW_POINT, GROUP_CURRENT + "#" + CHANNEL_HUMIDITY,
                GROUP_CURRENT + "#" + CHANNEL_PRESSURE, GROUP_CURRENT + "#" + CHANNEL_WIND_SPEED,
                GROUP_CURRENT + "#" + CHANNEL_WIND_DIRECTION, GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_SPEED,
                GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_DIRECTION, GROUP_CURRENT + "#" + CHANNEL_PRECIPITATION,
                GROUP_CURRENT + "#" + CHANNEL_CLOUD_COVER, GROUP_CURRENT + "#" + CHANNEL_VISIBILITY,
                GROUP_CURRENT + "#" + CHANNEL_SUNSHINE, GROUP_CURRENT + "#" + CHANNEL_SOLAR,
                GROUP_CURRENT + "#" + CHANNEL_CONDITION, GROUP_CURRENT + "#" + CHANNEL_ICON,
                GROUP_CURRENT + "#" + CHANNEL_OBSERVATION_TIME)) {
            State state = states.get(channelId);
            assert UnDefType.UNDEF.equals(state) : "Channel " + channelId + " expected UNDEF but got: " + state;
        }
    }

    /**
     * When the API returns an HTTP error, the Thing must go OFFLINE with COMMUNICATION_ERROR.
     */
    @Test
    void apiErrorThingGoesOffline() throws Exception {
        when(apiClient.getCurrentWeather(52.52, 13.41)).thenThrow(
                new org.openhab.binding.brightsky.internal.api.BrightSkyApiException("HTTP 503 from BrightSky API"));

        handler.initialize();
        handler.poll();

        verify(callback, atLeastOnce()).statusUpdated(eq(thing),
                argThat(status -> status.getStatus() == ThingStatus.OFFLINE
                        && status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));

        verify(callback, never()).stateUpdated(any(), any());
    }

    /**
     * Invalid configuration must set Thing to OFFLINE with CONFIGURATION_ERROR in initialize().
     */
    @Test
    void invalidConfigThingGoesOfflineWithConfigError() {
        when(thing.getConfiguration()).thenReturn(new Configuration(
                Map.of(CONFIG_LATITUDE, 999.0, CONFIG_LONGITUDE, 13.41, CONFIG_REFRESH_INTERVAL, 30)));

        handler.initialize();

        verify(callback).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.OFFLINE
                && status.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));

        verifyNoInteractions(apiClient);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Map<String, State> buildStateMap(List<ChannelUID> channels, List<State> states) {
        java.util.HashMap<String, State> map = new java.util.HashMap<>();
        for (int i = 0; i < channels.size(); i++) {
            map.put(channels.get(i).getId(), states.get(i));
        }
        return map;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void assertQuantity(Map<String, State> states, String channelId, double expectedValue,
            javax.measure.Unit expectedUnit) {
        State state = states.get(channelId);
        assert state instanceof QuantityType : "Channel " + channelId + ": expected QuantityType but got " + state;
        QuantityType qty = (QuantityType) state;
        QuantityType converted = qty.toUnit(expectedUnit);
        assert converted != null : "Channel " + channelId + ": cannot convert to " + expectedUnit;
        assert Math.abs(converted.doubleValue() - expectedValue) < 0.001
                : "Channel " + channelId + ": expected " + expectedValue + " " + expectedUnit + " but got " + converted;
    }

    private void assertStringState(Map<String, State> states, String channelId, String expectedValue) {
        State state = states.get(channelId);
        assert state instanceof StringType : "Channel " + channelId + ": expected StringType but got " + state;
        assert expectedValue.equals(((StringType) state).toString())
                : "Channel " + channelId + ": expected '" + expectedValue + "' but got '" + state + "'";
    }

    private CurrentWeatherResponse buildResponse() {
        WeatherRecord weather = new WeatherRecord();
        weather.timestamp = TIMESTAMP;
        weather.temperature = TEMPERATURE;
        weather.dewPoint = DEW_POINT;
        weather.relativeHumidity = HUMIDITY;
        weather.pressureMsl = PRESSURE_HPA;
        weather.windSpeed10 = WIND_SPEED;
        weather.windDirection10 = WIND_DIRECTION;
        weather.windGustSpeed10 = WIND_GUST_SPEED;
        weather.windGustDirection10 = WIND_GUST_DIRECTION;
        weather.precipitation10 = PRECIPITATION;
        weather.cloudCover = CLOUD_COVER;
        weather.visibility = VISIBILITY;
        weather.sunshine30 = SUNSHINE;
        weather.solar30 = SOLAR;
        weather.condition = CONDITION;
        weather.icon = ICON;

        Source source = new Source();
        source.stationName = "Berlin-Tempelhof";
        source.dwdStationId = "00433";
        source.distance = 5858.0;

        CurrentWeatherResponse response = new CurrentWeatherResponse();
        response.weather = weather;
        response.sources = List.of(source);
        return response;
    }
}
