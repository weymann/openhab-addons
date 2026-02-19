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

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
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
        scheduler.execute(this::refresh);
    }

    @Override
    public void dispose() {
        // nothing to dispose
    }

    private void refresh() {
        switch (config.priceProvider) {
            case "energychartsinfo":
                fetchEnergyChartsInfoDataPrices();
                break;
            case "energyforecast":
                fetchEnergyForecastPrices();
                break;
            default:
                logger.warn("Unsupported price provider: {}", config.priceProvider);
        }
    }

    private void fetchEnergyForecastPrices() {
        try {
            ContentResponse response = httpClient
                    .GET("https://www.energyforecast.de/api/v1/predictions/next_48_hours?token=" + config.token);
            sendTimeSeries(EnergyChartsInfoBindingConstants.CHANNEL_PRICE, decodeA(response.getContentAsString()));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error while fetching energy charts info data: {}", e.getMessage());
        }
    }

    private void fetchEnergyChartsInfoDataPrices() {
        String url = EnergyChartsInfoBindingConstants.CHARTS_INFO_URL + "price?bzn=" + config.region;
        try {
            ContentResponse response = httpClient.GET(url);
            sendTimeSeries(EnergyChartsInfoBindingConstants.CHANNEL_PRICE, decodeB(response.getContentAsString()));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error while fetching energy charts info data: {}", e.getMessage());
        }
    }

    public static TimeSeries decodeA(String prices) {
        JSONArray priceArray = new JSONArray(prices);
        TimeSeries timeSeries = new TimeSeries(TimeSeries.Policy.REPLACE);
        priceArray.forEach(item -> {
            JSONObject jsonObject = (JSONObject) item;
            Instant start = Instant.parse(jsonObject.getString("start"));
            timeSeries.add(start, QuantityType.valueOf(jsonObject.getDouble("price") + " EUR/kWh"));

        });
        return timeSeries;
    }

    public static TimeSeries decodeB(String prices) {
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
}
