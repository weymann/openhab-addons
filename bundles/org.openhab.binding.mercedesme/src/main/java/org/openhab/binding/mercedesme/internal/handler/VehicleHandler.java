/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.mercedesme.internal.handler;

import static org.openhab.binding.mercedesme.internal.Constants.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.MercedesMeCommandOptionProvider;
import org.openhab.binding.mercedesme.internal.MercedesMeStateOptionProvider;
import org.openhab.binding.mercedesme.internal.config.VehicleConfiguration;
import org.openhab.binding.mercedesme.internal.utils.ChannelStateMap;
import org.openhab.binding.mercedesme.internal.utils.Mapper;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class VehicleHandler extends BaseThingHandler {
    private static final String EXT_IMG_RES = "ExtImageResources_";
    private static final String INITIALIZE_COMMAND = "Initialze";

    private final Logger logger = LoggerFactory.getLogger(VehicleHandler.class);
    private final Map<String, Long> timeHash = new HashMap<String, Long>();
    private final MercedesMeCommandOptionProvider mmcop;
    private final MercedesMeStateOptionProvider mmsop;
    private final TimeZoneProvider timeZoneProvider;
    private final StorageService storageService;
    private final HttpClient httpClient;
    private final String uid;

    private Optional<ScheduledFuture<?>> refreshJob = Optional.empty();
    private Optional<AccountHandler> accountHandler = Optional.empty();
    private Optional<QuantityType<?>> rangeElectric = Optional.empty();
    private Optional<Storage<String>> imageStorage = Optional.empty();
    private Optional<VehicleConfiguration> config = Optional.empty();
    private Optional<QuantityType<?>> rangeFuel = Optional.empty();
    private Instant nextRefresh;
    private boolean online = false;

    public VehicleHandler(Thing thing, HttpClient hc, String uid, StorageService storageService,
            MercedesMeCommandOptionProvider mmcop, MercedesMeStateOptionProvider mmsop, TimeZoneProvider tzp) {
        super(thing);
        httpClient = hc;
        this.uid = uid;
        this.mmcop = mmcop;
        this.mmsop = mmsop;
        timeZoneProvider = tzp;
        this.storageService = storageService;
        nextRefresh = Instant.now();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.trace("Received {} {} {}", channelUID.getAsString(), command.toFullString(), channelUID.getId());
        if (command instanceof RefreshType) {
            /**
             * Refresh requested e.g. after adding new item
             * Adding several items will frequently raise RefreshType command. Calling API each time shall be avoided
             * API update is performed after 5 seconds for all items which should be sufficient for a frequent update
             */
            if (Instant.now().isAfter(nextRefresh)) {
                nextRefresh = Instant.now().plus(Duration.ofSeconds(5));
                logger.trace("Refresh granted - next at {}", nextRefresh);
                scheduler.schedule(this::getData, 5, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void initialize() {
        config = Optional.of(getConfigAs(VehicleConfiguration.class));
        Bridge bridge = getBridge();
        if (bridge != null) {
            updateStatus(ThingStatus.UNKNOWN);
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                accountHandler = Optional.of((AccountHandler) handler);
                startSchedule(config.get().refreshInterval);
                updateState(new ChannelUID(thing.getUID(), GROUP_IMAGE, "clear-cache"), OnOffType.OFF);
            } else {
                throw new IllegalStateException("BridgeHandler is null");
            }
        } else {
            String textKey = Constants.STATUS_TEXT_PREFIX + "vehicle" + Constants.STATUS_BRIDGE_MISSING;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, textKey);
        }
    }

    private void startSchedule(int interval) {
        refreshJob.ifPresentOrElse(job -> {
            if (job.isCancelled()) {
                refreshJob = Optional
                        .of(scheduler.scheduleWithFixedDelay(this::getData, 0, interval, TimeUnit.MINUTES));
            } // else - scheduler is already running!
        }, () -> {
            refreshJob = Optional.of(scheduler.scheduleWithFixedDelay(this::getData, 0, interval, TimeUnit.MINUTES));
        });
    }

    @Override
    public void dispose() {
        refreshJob.ifPresent(job -> job.cancel(true));
    }

    public void getData() {
        if (accountHandler.isEmpty()) {
            logger.warn("AccountHandler not set");
            return;
        }
        String token = "abc";
        if (token.isEmpty()) {
            String textKey = Constants.STATUS_TEXT_PREFIX + "vehicle" + Constants.STATUS_BRIDGE_ATHORIZATION;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
            return;
        } else if (!online) { // only update if thing isn't already ONLINE
            updateStatus(ThingStatus.ONLINE);
        }

        // Mileage for all cars
        String odoUrl = String.format(ODO_URL, config.get().vin);
        if (accountConfigAvailable()) {
        } else {
            logger.trace("{} Account not properly configured", this.getThing().getLabel());
        }

        // Electric status for hybrid and electric
        if (uid.equals(BEV) || uid.equals(HYBRID)) {
            String evUrl = String.format(EV_URL, config.get().vin);
            if (accountConfigAvailable()) {
            } else {
                logger.trace("{} Account not properly configured", this.getThing().getLabel());
            }
        }

        // Fuel for hybrid and combustion
        if (uid.equals(COMBUSTION) || uid.equals(HYBRID)) {
            String fuelUrl = String.format(FUEL_URL, config.get().vin);
            if (accountConfigAvailable()) {
            } else {
                logger.trace("{} Account not properly configured", this.getThing().getLabel());
            }
        }

        // Status and Lock for all
        String statusUrl = String.format(STATUS_URL, config.get().vin);
        if (accountConfigAvailable()) {
        } else {
            logger.trace("{} Account not properly configured", this.getThing().getLabel());
        }
        String lockUrl = String.format(LOCK_URL, config.get().vin);
        if (accountConfigAvailable()) {
        } else {
            logger.trace("{} Account not properly configured", this.getThing().getLabel());
        }

        // Range radius for all types
        updateRadius();
    }

    private boolean accountConfigAvailable() {
        if (accountHandler.isPresent()) {
            if (accountHandler.get().config.isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void call(String url) {
        String requestUrl = String.format(url, config.get().vin);
        // Calculate endpoint for debugging
        String[] endpoint = requestUrl.split("/");
        String finalEndpoint = endpoint[endpoint.length - 1];
        // debug prefix contains Thing label and call endpoint for propper debugging
        String debugPrefix = this.getThing().getLabel() + Constants.COLON + finalEndpoint;

        Request req = httpClient.newRequest(requestUrl);
        req.header(HttpHeader.AUTHORIZATION, "Bearer " + "abc");
        try {
            ContentResponse cr = req.send();
            logger.trace("{} Response {} {}", debugPrefix, cr.getStatus(), cr.getContentAsString());
            if (cr.getStatus() == 200) {
                distributeContent(cr.getContentAsString().trim());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.info("{} Error getting data {}", debugPrefix, e.getMessage());
            fallbackCall(requestUrl);
        }
    }

    /**
     * Fallback solution with Java11 classes
     * Performs try with Java11 HttpClient - https://zetcode.com/java/getpostrequest/ to identify Community problem
     * https://community.openhab.org/t/mercedes-me-binding/136852/21
     *
     * @param requestUrl
     */
    private void fallbackCall(String requestUrl) {
        // Calculate endpoint for debugging
        String[] endpoint = requestUrl.split("/");
        String finalEndpoint = endpoint[endpoint.length - 1];
        // debug prefix contains Thing label and call endpoint for propper debugging
        String debugPrefix = this.getThing().getLabel() + Constants.COLON + finalEndpoint;

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(requestUrl))
                .header(HttpHeader.AUTHORIZATION.toString(), "Bearer " + "abc").GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("{} Fallback Response {} {}", debugPrefix, response.statusCode(), response.body());
            if (response.statusCode() == 200) {
                distributeContent(response.body().trim());
            }
        } catch (IOException | InterruptedException e) {
            logger.warn("{} Error getting data via fallback {}", debugPrefix, e.getMessage());
        }
    }

    private void distributeContent(String json) {
        if (json.startsWith("[") && json.endsWith("]")) {
            JSONArray ja = new JSONArray(json);
            for (Iterator<Object> iterator = ja.iterator(); iterator.hasNext();) {
                JSONObject jo = (JSONObject) iterator.next();
                ChannelStateMap csm = Mapper.getChannelStateMap(jo);
                if (csm.isValid()) {
                    updateChannel(csm);

                    /**
                     * handle some specific channels
                     */
                    // store ChannelMap for range radius calculation
                    String channel = csm.getChannel();
                    if ("range-electric".equals(channel)) {
                        rangeElectric = Optional.of((QuantityType<?>) csm.getState());
                    } else if ("range-fuel".equals(channel)) {
                        rangeFuel = Optional.of((QuantityType<?>) csm.getState());
                    } else if ("soc".equals(channel)) {
                        if (config.get().batteryCapacity > 0) {
                            float socValue = ((QuantityType<?>) csm.getState()).floatValue();
                            float batteryCapacity = config.get().batteryCapacity;
                            float chargedValue = Math.round(socValue * 1000 * batteryCapacity / 1000) / (float) 100;
                            ChannelStateMap charged = new ChannelStateMap("charged", GROUP_RANGE,
                                    QuantityType.valueOf(chargedValue, Units.KILOWATT_HOUR), csm.getTimestamp());
                            updateChannel(charged);
                            float unchargedValue = Math.round((100 - socValue) * 1000 * batteryCapacity / 1000)
                                    / (float) 100;
                            ChannelStateMap uncharged = new ChannelStateMap("uncharged", GROUP_RANGE,
                                    QuantityType.valueOf(unchargedValue, Units.KILOWATT_HOUR), csm.getTimestamp());
                            updateChannel(uncharged);
                        } else {
                            logger.debug("No battery capacity given");
                        }
                    } else if ("fuel-level".equals(channel)) {
                        if (config.get().fuelCapacity > 0) {
                            float fuelLevelValue = ((QuantityType<?>) csm.getState()).floatValue();
                            float fuelCapacity = config.get().fuelCapacity;
                            float litersInTank = Math.round(fuelLevelValue * 1000 * fuelCapacity / 1000) / (float) 100;
                            ChannelStateMap tankFilled = new ChannelStateMap("tank-remain", GROUP_RANGE,
                                    QuantityType.valueOf(litersInTank, Units.LITRE), csm.getTimestamp());
                            updateChannel(tankFilled);
                            float litersFree = Math.round((100 - fuelLevelValue) * 1000 * fuelCapacity / 1000)
                                    / (float) 100;
                            ChannelStateMap tankOpen = new ChannelStateMap("tank-open", GROUP_RANGE,
                                    QuantityType.valueOf(litersFree, Units.LITRE), csm.getTimestamp());
                            updateChannel(tankOpen);
                        } else {
                            logger.debug("No fuel capacity given");
                        }
                    }
                } else {
                    logger.warn("Unable to deliver state for {}", jo);
                }
            }
        } else {
            logger.debug("JSON Array expected but received {}", json);
        }
    }

    private void updateRadius() {
        if (rangeElectric.isPresent()) {
            // update electric radius
            ChannelStateMap radiusElectric = new ChannelStateMap("radius-electric", GROUP_RANGE,
                    guessRangeRadius(rangeElectric.get()), 0);
            updateChannel(radiusElectric);
            if (rangeFuel.isPresent()) {
                // update fuel & hybrid radius
                ChannelStateMap radiusFuel = new ChannelStateMap("radius-fuel", GROUP_RANGE,
                        guessRangeRadius(rangeFuel.get()), 0);
                updateChannel(radiusFuel);
                int hybridKm = rangeElectric.get().intValue() + rangeFuel.get().intValue();
                QuantityType<Length> hybridRangeState = QuantityType.valueOf(hybridKm, KILOMETRE_UNIT);
                ChannelStateMap rangeHybrid = new ChannelStateMap("range-hybrid", GROUP_RANGE, hybridRangeState, 0);
                updateChannel(rangeHybrid);
                ChannelStateMap radiusHybrid = new ChannelStateMap("radius-hybrid", GROUP_RANGE,
                        guessRangeRadius(hybridRangeState), 0);
                updateChannel(radiusHybrid);
            }
        } else if (rangeFuel.isPresent()) {
            // update fuel & hybrid radius
            ChannelStateMap radiusFuel = new ChannelStateMap("radius-fuel", GROUP_RANGE,
                    guessRangeRadius(rangeFuel.get()), 0);
            updateChannel(radiusFuel);
        }
    }

    /**
     * Easy function but there's some measures behind:
     * Guessing the range of the Vehicle on Map. If you can drive x kilometers with your Vehicle it's not feasible to
     * project this x km Radius on Map. The roads to be taken are causing some overhead because they are not a straight
     * line from Location A to B.
     * I've taken some measurements to calculate the overhead factor based on Google Maps
     * Berlin - Dresden: Road Distance: 193 air-line Distance 167 = Factor 87%
     * Kassel - Frankfurt: Road Distance: 199 air-line Distance 143 = Factor 72%
     * After measuring more distances you'll find out that the outcome is between 70% and 90%. So
     *
     * This depends also on the roads of a concrete route but this is only a guess without any Route Navigation behind
     *
     * @param range
     * @return mapping from air-line distance to "real road" distance
     */
    public static State guessRangeRadius(QuantityType<?> s) {
        double radius = s.intValue() * 0.8;
        return QuantityType.valueOf(Math.round(radius), KILOMETRE_UNIT);
    }

    protected void updateChannel(ChannelStateMap csm) {
        updateTime(csm.getGroup(), csm.getTimestamp());
        updateState(new ChannelUID(thing.getUID(), csm.getGroup(), csm.getChannel()), csm.getState());
    }

    private void updateTime(String group, long timestamp) {
        boolean updateTime = false;
        Long l = timeHash.get(group);
        if (l != null) {
            if (l.longValue() < timestamp) {
                updateTime = true;
            }
        } else {
            updateTime = true;
        }
        if (updateTime) {
            timeHash.put(group, timestamp);
            DateTimeType dtt = new DateTimeType(Instant.ofEpochMilli(timestamp).atZone(timeZoneProvider.getTimeZone()));
            updateState(new ChannelUID(thing.getUID(), group, "last-update"), dtt);
        }
    }

    @Override
    public void updateStatus(ThingStatus ts, ThingStatusDetail tsd, @Nullable String details) {
        online = ts.equals(ThingStatus.ONLINE);
        super.updateStatus(ts, tsd, details);
    }
}
