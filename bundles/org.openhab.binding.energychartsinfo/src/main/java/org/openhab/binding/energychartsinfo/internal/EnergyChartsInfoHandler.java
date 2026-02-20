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
package org.openhab.binding.energychartsinfo.internal;

import static org.openhab.binding.energychartsinfo.internal.EnergyChartsInfoBindingConstants.*;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.TimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnergyChartsInfoHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EnergyChartsInfoHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EnergyChartsInfoHandler.class);
    private final TimeZoneProvider tzp;
    private final HttpClient httpClient;

    private @Nullable ScheduledFuture<?> refreshJob;

    private EnergyChartsInfoConfiguration config = new EnergyChartsInfoConfiguration();

    public EnergyChartsInfoHandler(Thing thing, HttpClient client, TimeZoneProvider tzp) {
        super(thing);
        this.httpClient = client;
        this.tzp = tzp;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // no commands supported
    }

    @Override
    public void initialize() {
        config = getConfigAs(EnergyChartsInfoConfiguration.class);
        updateStatus(ThingStatus.ONLINE);
        refreshJob = scheduler.scheduleWithFixedDelay(this::refresh, 0, config.refreshInterval, TimeUnit.MINUTES);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> localRefreshJob = refreshJob;
        if (localRefreshJob != null) {
            localRefreshJob.cancel(true);
        }
    }

    private void refresh() {
        fetchEnergyChartsInfoDataPrices();
        fetchRenewableShares();
        if (!config.token.isEmpty()) {
            fetchEnergyForecastPrices();
        }
    }

    private void fetchEnergyChartsInfoDataPrices() {
        try {
            Request dayAheadRequest = httpClient.newRequest(CHARTS_INFO_URL + "price").timeout(10, TimeUnit.SECONDS);
            dayAheadRequest.param("bzn", config.zone);
            ContentResponse response = dayAheadRequest.send();
            logger.info("Energy charts info pricare response: {}", response.getStatus());
            sendTimeSeries(CHANNEL_GROUP_PRICE + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_DAY_AHEAD,
                    decodeEnergyChartPrices(response.getContentAsString()));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error while fetching energy charts info data: {}", e.getMessage());
        }
    }

    private void fetchEnergyForecastPrices() {
        try {
            Request forecastRequest = httpClient.newRequest(ENERGY_FORECAST_URL).timeout(10, TimeUnit.SECONDS);
            forecastRequest.param("fixed_cost_cent", String.format(Locale.US, "%.2f", config.fixCost));
            forecastRequest.param("vat", String.format(Locale.US, "%.2f", config.vat));
            forecastRequest.param("resolution", "QUARTER_HOURLY");
            forecastRequest.param("market_zone", config.zone);
            forecastRequest.param("token", config.token);

            ContentResponse response = forecastRequest.send();
            int responseStatus = response.getStatus();
            logger.info("Energy price forecast response: {}", response.getStatus());
            sendTimeSeries(CHANNEL_GROUP_PRICE + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_FORECAST,
                    decodeEnergyForecastPrices(response.getContentAsString()));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error while fetching energy charts info data: {}", e.getMessage());
        }
    }

    private void fetchRenewableShares() {
        try {
            Request renewableShareRequest = httpClient.newRequest(CHARTS_INFO_URL + "ren_share_forecast").timeout(10,
                    TimeUnit.SECONDS);
            renewableShareRequest.param("country", config.country);
            ContentResponse response = renewableShareRequest.send();
            logger.info("Renewable shares response: {}", response.getStatus());
            JSONObject sharesJson = new JSONObject(response.getContentAsString());
            sendTimeSeries(CHANNEL_GROUP_RENEWABLES + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_REN_SHARE,
                    decodeRenewableShares(sharesJson, "ren_share"));
            sendTimeSeries(CHANNEL_GROUP_RENEWABLES + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_SOLAR_SHARE,
                    decodeRenewableShares(sharesJson, "solar_share"));
            sendTimeSeries(CHANNEL_GROUP_RENEWABLES + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_WIND_ONSHORE_SHARE,
                    decodeRenewableShares(sharesJson, "wind_onshore_share"));
            sendTimeSeries(CHANNEL_GROUP_RENEWABLES + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_WIND_OFFSHORE_SHARE,
                    decodeRenewableShares(sharesJson, "wind_offshore_share"));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error while fetching energy charts info data: {}", e.getMessage());
        }
    }

    public static TimeSeries decodeEnergyChartPrices(String prices) {
        JSONObject pricesJson = new JSONObject(prices);
        TimeSeries timeSeries = new TimeSeries(TimeSeries.Policy.REPLACE);
        JSONArray timestanps = pricesJson.getJSONArray("unix_seconds");
        for (int i = 0; i < timestanps.length(); i++) {
            Instant start = Instant.ofEpochSecond(((Number) timestanps.get(i)).longValue());
            double price = pricesJson.getJSONArray("price").getDouble(i) / 1000.0;
            timeSeries.add(start, QuantityType.valueOf(price + " EUR/kWh"));
        }
        return timeSeries;
    }

    public static TimeSeries decodeRenewableShares(JSONObject shares, String type) {
        TimeSeries timeSeries = new TimeSeries(TimeSeries.Policy.REPLACE);
        JSONArray timestanps = shares.getJSONArray("unix_seconds");
        JSONArray shareForType = shares.getJSONArray(type);
        for (int i = 0; i < shareForType.length(); i++) {
            Instant start = Instant.ofEpochSecond(((Number) timestanps.get(i)).longValue());
            double share = shareForType.getDouble(i);
            timeSeries.add(start, QuantityType.valueOf(share + " %"));
        }
        return timeSeries;
    }

    public static TimeSeries decodeEnergyForecastPrices(String prices) {
        JSONArray priceArray = new JSONArray(prices);
        TimeSeries timeSeries = new TimeSeries(TimeSeries.Policy.REPLACE);
        priceArray.forEach(item -> {
            JSONObject jsonObject = (JSONObject) item;
            Instant start = Instant.parse(jsonObject.getString("start"));
            timeSeries.add(start, QuantityType.valueOf(jsonObject.getDouble("price") + " EUR/kWh"));

        });
        return timeSeries;
    }
}
