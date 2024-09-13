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
package org.openhab.binding.entsoe.internal.handler;

import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.API_TIMEOUT;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.CHANNEL_EVENT;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.CHANNEL_EVENT_DAY_AHEAD;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.CHANNEL_GROUP_ELECTRICITY;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.CHANNEL_SPOT_PRICE;
import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.CHANNEL_UPDATED;
import static org.openhab.core.types.TimeSeries.Policy.REPLACE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.entsoe.internal.client.Client;
import org.openhab.binding.entsoe.internal.client.Request;
import org.openhab.binding.entsoe.internal.config.EntsoEConfiguration;
import org.openhab.binding.entsoe.internal.exception.EntsoEConfigurationException;
import org.openhab.binding.entsoe.internal.exception.EntsoEResponseException;
import org.openhab.binding.entsoe.internal.exception.EntsoEUnexpectedException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.CurrencyUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EntsoEHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Miika Jukka - Initial contribution
 */
@NonNullByDefault
public class EntsoEHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EntsoEHandler.class);

    private static DateTimeFormatter LOG_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm");

    private EntsoEConfiguration config = new EntsoEConfiguration();
    private @Nullable ScheduledFuture<?> refreshJob;
    private Map<String, State> channelData = new HashMap<>();

    public EntsoEHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("ChannelUID: {}, Command: {}", channelUID, command);
        if (command instanceof RefreshType) {

        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        String channelID = channelUID.getId();
        State state = channelData.getOrDefault(channelID, UnDefType.UNDEF);
        if (state.equals(UnDefType.UNDEF)) {
            logger.debug("Channel map has no match for key \"{}\"", channelID);
        }
        updateState(channelUID, state);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::queryPricesAndUpdateChannels);
    }

    @Override
    public void dispose() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        super.dispose();
    }

    private void schedule(boolean success) {
        if (!success) {
            refreshJob = scheduler.schedule(this::queryPricesAndUpdateChannels, 60, TimeUnit.SECONDS);
        } else {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime nextRun = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            Duration duration = Duration.between(now, nextRun);
            refreshJob = scheduler.schedule(this::queryPricesAndUpdateChannels, duration.getSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    // String token = "9c2a9cdd-a33f-4253-b947-e7fdb8890b70";

    /**
     *
     *
     */
    private synchronized void queryPricesAndUpdateChannels() {
        boolean success = false;
        EntsoEConfiguration config = getConfigAs(EntsoEConfiguration.class);

        ZonedDateTime start = ZonedDateTime.now().minusDays(1).withHour(22);
        ZonedDateTime end = ZonedDateTime.now().plusDays(2);

        Request request = new Request(config.securityToken, config.area, start, end);
        Client client = new Client();

        logger.debug("Start: {}, end: {}", start, end);

        try {
            TreeMap<ZonedDateTime, Double> responseMap = client.doGetRequest(request, API_TIMEOUT);
            TimeSeries timesSeries = new TimeSeries(REPLACE);
            responseMap.forEach((time, price) -> {
                double priceKwh = price / 1000; // convert price €/mWh to €/kWh
                timesSeries.add(time.toInstant(), QuantityType.valueOf(priceKwh, CurrencyUnits.BASE_ENERGY_PRICE));
            });
            sendTimeSeries(CHANNEL_GROUP_ELECTRICITY + "#" + CHANNEL_SPOT_PRICE, timesSeries);
            updateState(CHANNEL_GROUP_ELECTRICITY + "#" + CHANNEL_UPDATED, new DateTimeType(ZonedDateTime.now()));
            triggerChannel(CHANNEL_GROUP_ELECTRICITY + "#" + CHANNEL_EVENT, CHANNEL_EVENT_DAY_AHEAD);
        } catch (

        EntsoEResponseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    String.format("%s", e.getMessage()));
            logger.error("{}", e.getMessage());
            e.printStackTrace();
        } catch (EntsoEUnexpectedException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    String.format("%s", e.getMessage()));
            logger.error("{}", e.getMessage());
        } catch (EntsoEConfigurationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    String.format("%s", e.getMessage()));
            logger.error("{}", e.getMessage());
            e.printStackTrace();
        } finally {
            schedule(success);
        }
    }
}
