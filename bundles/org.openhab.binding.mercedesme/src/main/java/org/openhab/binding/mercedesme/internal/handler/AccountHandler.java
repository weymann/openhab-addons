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
package org.openhab.binding.mercedesme.internal.handler;

import static org.openhab.binding.mercedesme.internal.Constants.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.api.Websocket;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.discovery.MercedesMeDiscoveryService;
import org.openhab.binding.mercedesme.internal.exception.MercedesMeAuthException;
import org.openhab.binding.mercedesme.internal.exception.MercedesMeException;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.daimler.mbcarkit.proto.Client.ClientMessage;
import com.daimler.mbcarkit.proto.Protos.AcknowledgeAssignedVehicles;
import com.daimler.mbcarkit.proto.VehicleEvents.AcknowledgeVEPUpdatesByVIN;
import com.daimler.mbcarkit.proto.VehicleEvents.PushMessage;
import com.daimler.mbcarkit.proto.VehicleEvents.VEPUpdate;
import com.daimler.mbcarkit.proto.Vehicleapi.AcknowledgeAppTwinCommandStatusUpdatesByVIN;
import com.daimler.mbcarkit.proto.Vehicleapi.AppTwinCommandStatusUpdatesByPID;
import com.daimler.mbcarkit.proto.Vehicleapi.AppTwinCommandStatusUpdatesByVIN;
import com.daimler.mbcarkit.proto.Vehicleapi.AppTwinPendingCommandsRequest;

/**
 * The {@link AccountHandler} acts as Bridge between MercedesMe Account and the associated vehicles
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AccountHandler extends BaseBridgeHandler implements AccessTokenRefreshListener {

    private final Logger logger = LoggerFactory.getLogger(AccountHandler.class);
    private final Map<String, Map<String, Object>> vinCapabilitiesMap = new HashMap<>();
    private final Map<String, VehicleHandler> activeVehicleHandlerMap = new HashMap<>();
    private final Map<String, VEPUpdate> vepUpdateMap = new HashMap<>();
    private final List<String> keepAliveList = new ArrayList<>();
    private final MercedesMeDiscoveryService discoveryService;
    private final LocaleProvider localeProvider;
    private final Storage<String> storage;
    private final HttpClient httpClient;

    private Optional<ScheduledFuture<?>> refreshScheduler = Optional.empty();
    private List<PushMessage> eventQueue = new ArrayList<>();
    private boolean updateRunning = false;

    private boolean disposed = true;

    Websocket api;
    AccountConfiguration config = new AccountConfiguration();

    public AccountHandler(Bridge bridge, MercedesMeDiscoveryService mmds, HttpClient hc, LocaleProvider lp,
            StorageService store) {
        super(bridge);
        discoveryService = mmds;
        httpClient = hc;
        localeProvider = lp;
        storage = store.getStorage(Constants.BINDING_ID);
        api = new Websocket(this, httpClient, config, localeProvider, storage);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        disposed = false;
        updateStatus(ThingStatus.UNKNOWN);
        config = getConfigAs(AccountConfiguration.class);
        String configValidReason = configValid();
        if (!configValidReason.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, configValidReason);
        } else {
            api = new Websocket(this, httpClient, config, localeProvider, storage);
            api.websocketDispose(false);
            scheduler.execute(this::refresh);
        }
    }

    /**
     * Refresh checking token validity, login in case of invalid token
     */
    public void refresh() {
        if (disposed) {
            logger.debug("AccountHandler is disposed, skipping refresh");
            return;
        }
        if (api.authTokenIsValid()) {
            // decide if we need to pull updates from vehicles or keep websocket alive
            if (keepAliveList.isEmpty()) {
                pullUpdates();
            } else {
                api.websocketUpdate();
            }
        } else {
            // token is not valid - try to resume login
            resume();
        }
        scheduleRefresh(nextRefreshSeconds());
    }

    private void scheduleRefresh(long delayInSeconds) {
        if (disposed) {
            logger.debug("AccountHandler is disposed, skipping scheduleRefresh");
            return;
        }
        refreshScheduler.ifPresent(job -> {
            job.cancel(false);
        });
        Instant nextSchedule = Instant.now().plus(delayInSeconds, ChronoUnit.SECONDS);
        logger.trace("Next schedule in {}/{} seconds at {}", delayInSeconds, config.refreshInterval, nextSchedule);
        refreshScheduler = Optional.of(scheduler.schedule(this::refresh, delayInSeconds, TimeUnit.SECONDS));
    }

    public void resume() {
        try {
            if (api.authLogin()) {
                api.websocketUpdate();
            } else {
                String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId()
                        + Constants.STATUS_LOGIN_FAILURE;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
            }
        } catch (MercedesMeAuthException e) {
            String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId()
                    + Constants.STATUS_LOGIN_EXCEPTION + " [\"" + e.getMessage() + "\"]";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
        }
    }

    private long nextRefreshSeconds() {
        // bring in 15% time variance
        int variance = config.refreshInterval * 60 / 15;
        long leftLimit = config.refreshInterval * 60 - variance;
        long rightLimit = config.refreshInterval * 60 + variance;
        return leftLimit + (long) (Math.random() * (rightLimit - leftLimit));
    }

    private String configValid() {
        config = getConfigAs(AccountConfiguration.class);
        String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId();
        if (Constants.NOT_SET.equals(config.email)) {
            return textKey + Constants.STATUS_EMAIL_MISSING;
        } else if (Constants.NOT_SET.equals(config.password)) {
            return textKey + Constants.STATUS_PASSWORD_MISSING;
        } else if (Constants.NOT_SET.equals(config.region)) {
            return textKey + Constants.STATUS_REGION_MISSING;
        } else if (config.refreshInterval < 5) {
            return textKey + Constants.STATUS_REFRESH_INVALID;
        } else {
            return Constants.EMPTY;
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        refreshScheduler.ifPresent(schedule -> {
            schedule.cancel(false);
        });
        refreshScheduler = Optional.empty();
        eventQueue.clear();
        api.websocketDispose(true);
    }

    @Override
    public void handleRemoval() {
        storage.remove(config.email);
        super.handleRemoval();
    }

    /**
     * https://next.openhab.org/javadoc/latest/org/openhab/core/auth/client/oauth2/package-summary.html
     */
    @Override
    public void onAccessTokenResponse(AccessTokenResponse tokenResponse) {
        if (Constants.NOT_SET.equals(tokenResponse.getAccessToken())) {
            String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId()
                    + Constants.STATUS_LOGIN_FAILURE;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
        }
    }

    public void registerVin(String vin, VehicleHandler handler) {
        discoveryService.vehicleRemove(this, vin, handler.getThing().getThingTypeUID().getId());
        activeVehicleHandlerMap.put(vin, handler);
        discovery(vin); // update properties for added vehicle
        VEPUpdate updateForVin = vepUpdateMap.get(vin);
        if (updateForVin != null) {
            handler.enqueueUpdate(updateForVin);
        } else {
            scheduleRefresh(1);
        }
    }

    public void unregisterVin(String vin) {
        activeVehicleHandlerMap.remove(vin);
    }

    @SuppressWarnings("null")
    public void getVehicleCapabilities(String vin) {
        if (storage.containsKey(vin + FEATURE_APPENDIX)) {
            if (activeVehicleHandlerMap.containsKey(vin)) {
                activeVehicleHandlerMap.get(vin).setFeatureCapabilities(storage.get(vin + FEATURE_APPENDIX));
            }
        }
        if (storage.containsKey(vin + COMMAND_APPENDIX)) {
            if (activeVehicleHandlerMap.containsKey(vin)) {
                activeVehicleHandlerMap.get(vin).setCommandCapabilities(storage.get(vin + COMMAND_APPENDIX));
            }
        }
    }

    /**
     * functions for websocket handling
     */

    public void enqueueMessage(PushMessage pm) {
        synchronized (eventQueue) {
            eventQueue.add(pm);
            scheduler.execute(this::scheduleMessage);
        }
    }

    private void scheduleMessage() {
        PushMessage pm;
        synchronized (eventQueue) {
            while (updateRunning) {
                try {
                    eventQueue.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    eventQueue.clear();
                    return;
                }
            }
            if (!eventQueue.isEmpty()) {
                pm = eventQueue.remove(0);
            } else {
                return;
            }
            updateRunning = true;
        }
        try {
            handleMessage(pm);
        } finally {
            synchronized (eventQueue) {
                updateRunning = false;
                eventQueue.notifyAll();
            }
        }
    }

    private void handleMessage(PushMessage pm) {
        if (pm.hasVepUpdates()) {
            boolean distributed = distributeVepUpdates(pm.getVepUpdates().getUpdatesMap());
            if (distributed) {
                AcknowledgeVEPUpdatesByVIN ack = AcknowledgeVEPUpdatesByVIN.newBuilder()
                        .setSequenceNumber(pm.getVepUpdates().getSequenceNumber()).build();
                ClientMessage cm = ClientMessage.newBuilder().setAcknowledgeVepUpdatesByVin(ack).build();
                api.sendAcknowledgeMessage(cm);
            }
        } else if (pm.hasAssignedVehicles()) {
            for (int i = 0; i < pm.getAssignedVehicles().getVinsCount(); i++) {
                String vin = pm.getAssignedVehicles().getVins(i);
                discovery(vin);
            }
            AcknowledgeAssignedVehicles ack = AcknowledgeAssignedVehicles.newBuilder().build();
            ClientMessage cm = ClientMessage.newBuilder().setAcknowledgeAssignedVehicles(ack).build();
            api.sendAcknowledgeMessage(cm);
        } else if (pm.hasApptwinCommandStatusUpdatesByVin()) {
            AppTwinCommandStatusUpdatesByVIN csubv = pm.getApptwinCommandStatusUpdatesByVin();
            commandStatusUpdate(csubv.getUpdatesByVinMap());
            AcknowledgeAppTwinCommandStatusUpdatesByVIN ack = AcknowledgeAppTwinCommandStatusUpdatesByVIN.newBuilder()
                    .setSequenceNumber(csubv.getSequenceNumber()).build();
            ClientMessage cm = ClientMessage.newBuilder().setAcknowledgeApptwinCommandStatusUpdateByVin(ack).build();
            api.sendAcknowledgeMessage(cm);
        } else if (pm.hasApptwinPendingCommandRequest()) {
            AppTwinPendingCommandsRequest pending = pm.getApptwinPendingCommandRequest();
            if (!pending.getAllFields().isEmpty()) {
                logger.trace("Pending Command {}", pending.getAllFields());
            }
        } else if (pm.hasDebugMessage()) {
            logger.trace("MB Debug Message: {}", pm.getDebugMessage().getMessage());
        } else {
            logger.trace("MB Message: {} not handled", pm.getAllFields());
        }
    }

    public boolean distributeVepUpdates(Map<String, VEPUpdate> map) {
        List<String> notFoundList = new ArrayList<>();
        map.forEach((key, value) -> {
            VehicleHandler h = activeVehicleHandlerMap.get(key);
            if (h != null) {
                h.enqueueUpdate(value);
            } else {
                if (value.getFullUpdate()) {
                    vepUpdateMap.put(key, value);
                }
                notFoundList.add(key);
            }
        });
        notFoundList.forEach(vin -> {
            discovery(vin); // add vehicle to discovery
            logger.trace("No VehicleHandler available for VIN {}", vin);
        });
        return notFoundList.isEmpty();
    }

    public void commandStatusUpdate(Map<String, AppTwinCommandStatusUpdatesByPID> updatesByVinMap) {
        updatesByVinMap.forEach((key, value) -> {
            VehicleHandler h = activeVehicleHandlerMap.get(key);
            if (h != null) {
                h.distributeCommandStatus(value);
            } else {
                logger.trace("No VehicleHandler available for VIN {}", key);
            }
        });
    }

    /**
     * Updates properties for existing handlers or delivers discovery result
     *
     * @param vin of discovered vehicle
     */
    @SuppressWarnings("null")
    public void discovery(String vin) {
        Map<String, Object> capabilities = vinCapabilitiesMap.get(vin);
        if (capabilities == null) {
            capabilities = api.restGetCapabilities(vin);
            vinCapabilitiesMap.put(vin, capabilities);
            if (activeVehicleHandlerMap.containsKey(vin)) {
                Map<String, String> properties = getStringCapabilities(capabilities);
                VehicleHandler vh = activeVehicleHandlerMap.get(vin);
                properties.putAll(vh.getThing().getProperties());
                vh.getThing().setProperties(properties);
            } else {
                Map<String, Object> discoveryProperties = new HashMap<>(capabilities);
                discoveryProperties.put("vin", vin);
                discoveryService.vehicleDiscovered(this, vin, discoveryProperties);
            }
        }
    }

    private Map<String, String> getStringCapabilities(Map<String, Object> props) {
        Map<String, String> stringProps = new HashMap<>();
        props.forEach((key, value) -> {
            stringProps.put(key, value.toString());
        });
        return stringProps;
    }

    public void sendCommand(@Nullable ClientMessage cm) {
        if (cm != null) {
            api.websocketAddCommand(cm);
        }
        scheduleRefresh(2);
    }

    public void keepAlive(String vin, boolean b) {
        if (b) {
            if (!keepAliveList.contains(vin)) {
                keepAliveList.add(vin);
                api.websocketKeepAlive(true);
                scheduleRefresh(1);
            }
        } else {
            keepAliveList.remove(vin);
            if (keepAliveList.isEmpty()) {
                api.websocketKeepAlive(false);
            }
        }
    }

    @Override
    public void updateStatus(ThingStatus ts) {
        super.updateStatus(ts);
    }

    @Override
    public void updateStatus(ThingStatus ts, ThingStatusDetail tsd, @Nullable String tsdt) {
        super.updateStatus(ts, tsd, tsdt);
    }

    private void pullUpdates() {
        activeVehicleHandlerMap.entrySet().forEach(entry -> {
            try {
                VEPUpdate update = api.restGetVehicleAttributes(entry.getKey());
                entry.getValue().enqueueUpdate(update);
                logger.trace("Pull update delivered {} updates", update.getAttributesCount());
                updateStatus(ThingStatus.ONLINE);
            } catch (MercedesMeException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/mercedesme.account.status.websocket-failure [\"" + e.getMessage() + "\"]");
            }
        });
    }

    /**
     * Vehicle Actions
     *
     * @param poi
     */
    public void sendPoi(String vin, JSONObject poi) {
        api.restSendPoi(vin, poi);
    }
}
