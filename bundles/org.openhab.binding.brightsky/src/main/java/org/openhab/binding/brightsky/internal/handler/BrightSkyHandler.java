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

import static org.openhab.binding.brightsky.internal.BrightSkyBindingConstants.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.brightsky.internal.api.BrightSkyApiClient;
import org.openhab.binding.brightsky.internal.api.BrightSkyApiException;
import org.openhab.binding.brightsky.internal.config.BrightSkyConfiguration;
import org.openhab.binding.brightsky.internal.dto.CurrentWeatherResponse;
import org.openhab.binding.brightsky.internal.dto.Source;
import org.openhab.binding.brightsky.internal.dto.WeatherRecord;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the {@code brightsky:weather-location} Thing.
 *
 * <p>
 * Polls the BrightSky API at a configurable interval and updates
 * channels in the {@code current} channel group with live DWD observations.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BrightSkyHandler extends BaseThingHandler {

    private static final int MIN_REFRESH_INTERVAL_MINUTES = 5;

    private final Logger logger = LoggerFactory.getLogger(BrightSkyHandler.class);
    private final HttpClientFactory httpClientFactory;

    private @Nullable BrightSkyApiClient apiClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable BrightSkyConfiguration config;

    /**
     * Creates a new handler.
     *
     * @param thing the Thing this handler manages
     * @param httpClientFactory framework-provided HTTP client factory
     */
    public BrightSkyHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super(thing);
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public void initialize() {
        BrightSkyConfiguration cfg = getConfigAs(BrightSkyConfiguration.class);
        this.config = cfg;

        String validationError = validateConfig(cfg);
        if (validationError != null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, validationError);
            return;
        }

        HttpClient httpClient = httpClientFactory.getCommonHttpClient();
        this.apiClient = createApiClient(httpClient);

        updateStatus(ThingStatus.UNKNOWN);

        // Schedule polling; first poll runs immediately (initialDelay = 0)
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, cfg.refreshInterval, TimeUnit.MINUTES);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = this.pollingJob;
        if (job != null) {
            job.cancel(true);
            this.pollingJob = null;
        }
        this.apiClient = null;
        this.config = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            poll();
        }
        // BrightSky is read-only - all other commands are ignored
    }

    // -------------------------------------------------------------------------
    // Protected factory - overridable in tests
    // -------------------------------------------------------------------------

    /**
     * Creates the {@link BrightSkyApiClient}. Protected to allow substitution in unit tests.
     *
     * @param httpClient the shared Jetty HTTP client
     * @return a new API client instance
     */
    protected BrightSkyApiClient createApiClient(HttpClient httpClient) {
        return new BrightSkyApiClient(httpClient);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Performs a single poll of the BrightSky API and updates all channels.
     * Called by the scheduler and on REFRESH commands.
     * Package-private to allow direct invocation in unit tests.
     */
    void poll() {
        BrightSkyApiClient client = this.apiClient;
        BrightSkyConfiguration cfg = this.config;
        if (client == null || cfg == null) {
            // dispose() was called between schedule and execution
            return;
        }

        try {
            CurrentWeatherResponse response = fetchWeather(client, cfg);
            logResolvedStation(response);

            WeatherRecord weather = response.weather;
            if (weather == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "API returned empty weather object");
                return;
            }

            updateChannels(weather);
            updateStatus(ThingStatus.ONLINE);

        } catch (BrightSkyApiException e) {
            logger.debug("BrightSky poll failed: {}", e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Fetches current weather using station ID if configured, otherwise lat/lon.
     */
    private CurrentWeatherResponse fetchWeather(BrightSkyApiClient client, BrightSkyConfiguration cfg)
            throws BrightSkyApiException {
        String stationId = cfg.stationId;
        if (stationId != null && !stationId.isBlank()) {
            return client.getCurrentWeatherByStation(stationId);
        }
        return client.getCurrentWeather(cfg.latitude, cfg.longitude);
    }

    /**
     * Logs the nearest DWD station resolved by BrightSky on the first successful response.
     * Helps users verify which physical station is supplying their data.
     */
    private void logResolvedStation(CurrentWeatherResponse response) {
        List<Source> sources = response.sources;
        if (sources != null && !sources.isEmpty()) {
            Source primary = sources.get(0);
            if (primary.stationName != null) {
                logger.info("BrightSky resolved nearest DWD station: {} (id={}, distance={}m)", primary.stationName,
                        primary.dwdStationId, primary.distance);
            }
        }
    }

    /**
     * Maps all {@link WeatherRecord} fields to openHAB channel states.
     * Any {@code null} field maps to {@link UnDefType#UNDEF}.
     */
    private void updateChannels(WeatherRecord w) {
        // Temperature channels
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_TEMPERATURE, w.temperature, SIUnits.CELSIUS);
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_DEW_POINT, w.dewPoint, SIUnits.CELSIUS);

        // Humidity and pressure
        @Nullable
        Integer relativeHumidity = w.relativeHumidity;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_HUMIDITY,
                relativeHumidity != null ? relativeHumidity.doubleValue() : null, Units.PERCENT);
        @Nullable
        Double pressureMsl = w.pressureMsl;
        // API returns hPa; SI unit is Pa - multiply by 100
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_PRESSURE, pressureMsl != null ? pressureMsl * 100.0 : null,
                SIUnits.PASCAL);

        // Wind (10-minute averages)
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_WIND_SPEED, w.windSpeed10, SIUnits.KILOMETRE_PER_HOUR);
        @Nullable
        Integer windDirection10 = w.windDirection10;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_WIND_DIRECTION,
                windDirection10 != null ? windDirection10.doubleValue() : null, Units.DEGREE_ANGLE);
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_SPEED, w.windGustSpeed10,
                SIUnits.KILOMETRE_PER_HOUR);
        @Nullable
        Integer windGustDirection10 = w.windGustDirection10;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_WIND_GUST_DIRECTION,
                windGustDirection10 != null ? windGustDirection10.doubleValue() : null, Units.DEGREE_ANGLE);

        // Precipitation (10-minute sum, mm to m)
        @Nullable
        Double precipitation10 = w.precipitation10;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_PRECIPITATION,
                precipitation10 != null ? precipitation10 * 0.001 : null, SIUnits.METRE);

        // Sky
        @Nullable
        Integer cloudCover = w.cloudCover;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_CLOUD_COVER,
                cloudCover != null ? cloudCover.doubleValue() : null, Units.PERCENT);
        @Nullable
        Integer visibility = w.visibility;
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_VISIBILITY,
                visibility != null ? visibility.doubleValue() : null, SIUnits.METRE);

        // Sunshine (30-minute sum, minutes)
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_SUNSHINE, w.sunshine30, Units.MINUTE);

        // Solar irradiance (30-minute sum, kWh/m2)
        updateQuantityOrUndef(GROUP_CURRENT + "#" + CHANNEL_SOLAR, w.solar30, MetricPrefix.KILO(Units.WATT_HOUR));

        // String channels
        String condition = w.condition;
        updateState(GROUP_CURRENT + "#" + CHANNEL_CONDITION,
                condition != null ? new StringType(condition) : UnDefType.UNDEF);

        String icon = w.icon;
        updateState(GROUP_CURRENT + "#" + CHANNEL_ICON, icon != null ? new StringType(icon) : UnDefType.UNDEF);

        // Observation timestamp
        String timestamp = w.timestamp;
        if (timestamp != null) {
            try {
                updateState(GROUP_CURRENT + "#" + CHANNEL_OBSERVATION_TIME,
                        new DateTimeType(ZonedDateTime.parse(timestamp)));
            } catch (DateTimeParseException e) {
                logger.debug("Could not parse observation timestamp '{}': {}", timestamp, e.getMessage());
                updateState(GROUP_CURRENT + "#" + CHANNEL_OBSERVATION_TIME, UnDefType.UNDEF);
            }
        } else {
            updateState(GROUP_CURRENT + "#" + CHANNEL_OBSERVATION_TIME, UnDefType.UNDEF);
        }
    }

    /**
     * Updates a {@link QuantityType} channel or sets it to {@link UnDefType#UNDEF}
     * when the value is {@code null}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateQuantityOrUndef(String channelId, @Nullable Double value, javax.measure.Unit unit) {
        if (value != null) {
            updateState(channelId, new QuantityType<>(value, unit));
        } else {
            updateState(channelId, UnDefType.UNDEF);
        }
    }

    /**
     * Validates the binding configuration.
     *
     * @param cfg configuration to validate
     * @return error message if invalid, {@code null} if valid
     */
    private @Nullable String validateConfig(BrightSkyConfiguration cfg) {
        String stationId = cfg.stationId;
        boolean hasStationId = stationId != null && !stationId.isBlank();

        if (!hasStationId) {
            if (cfg.latitude < -90 || cfg.latitude > 90) {
                return "latitude must be between -90 and 90, got: " + cfg.latitude;
            }
            if (cfg.longitude < -180 || cfg.longitude > 180) {
                return "longitude must be between -180 and 180, got: " + cfg.longitude;
            }
        }

        if (cfg.refreshInterval < MIN_REFRESH_INTERVAL_MINUTES) {
            return "refreshInterval must be at least " + MIN_REFRESH_INTERVAL_MINUTES + " minutes, got: "
                    + cfg.refreshInterval;
        }

        return null;
    }
}
