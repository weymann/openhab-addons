/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.discovery.MercedesMeDiscoveryService;
import org.openhab.binding.mercedesme.internal.server.AuthService;
import org.openhab.binding.mercedesme.internal.server.MBWebsocket;
import org.openhab.binding.mercedesme.internal.utils.Utils;
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
    private static final String FEATURE_APPENDIX = "-features";
    private static final String COMMAND_APPENDIX = "-commands";

    private final Logger logger = LoggerFactory.getLogger(AccountHandler.class);
    private final MercedesMeDiscoveryService discoveryService;
    private final HttpClient httpClient;
    private final LocaleProvider localeProvider;
    private final Storage<String> storage;
    private final Map<String, VehicleHandler> activeVehicleHandlerMap = new HashMap<>();
    private final Map<String, VEPUpdate> vepUpdateMap = new HashMap<>();
    private final Map<String, Map<String, Object>> capabilitiesMap = new HashMap<>();

    private Optional<ScheduledFuture<?>> refreshScheduler = Optional.empty();
    private List<PushMessage> eventQueue = new ArrayList<>();
    private boolean updateRunning = false;

    private String capabilitiesEndpoint = "/v1/vehicle/%s/capabilities";
    private String commandCapabilitiesEndpoint = "/v1/vehicle/%s/capabilities/commands";
    private String poiEndpoint = "/v1/vehicle/%s/route";

    Optional<AuthService> authService = Optional.empty();
    final MBWebsocket mbWebsocket;
    AccountConfiguration config = new AccountConfiguration();
    @Nullable
    ClientMessage message;

    public AccountHandler(Bridge bridge, MercedesMeDiscoveryService mmds, HttpClient hc, LocaleProvider lp,
            StorageService store) {
        super(bridge);
        discoveryService = mmds;
        httpClient = hc;
        mbWebsocket = new MBWebsocket(this, hc);
        localeProvider = lp;
        storage = store.getStorage(Constants.BINDING_ID);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        config = getConfigAs(AccountConfiguration.class);
        String configValidReason = configValid();
        if (!configValidReason.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, configValidReason);
        } else {
            mbWebsocket.dispose(false);
            authService = Optional.of(new AuthService(this, httpClient, config, localeProvider.getLocale(), storage,
                    config.refreshToken));
            refreshScheduler = Optional
                    .of(scheduler.scheduleWithFixedDelay(this::refresh, 0, config.refreshInterval, TimeUnit.MINUTES));
        }
    }

    public void refresh() {
        if (!Constants.NOT_SET.equals(authService.get().getToken())) {
            mbWebsocket.update();
        } else {
            // all failed - start manual authorization
            String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId()
                    + Constants.STATUS_AUTH_NEEDED;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
        }
    }

    private String configValid() {
        config = getConfigAs(AccountConfiguration.class);
        String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId();
        if (Constants.NOT_SET.equals(config.refreshToken)) {
            return textKey + Constants.STATUS_REFRESH_TOKEN_MISSING;
        } else if (Constants.NOT_SET.equals(config.email)) {
            return textKey + Constants.STATUS_EMAIL_MISSING;
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
        refreshScheduler.ifPresent(schedule -> {
            if (!schedule.isCancelled()) {
                schedule.cancel(true);
            }
        });
        eventQueue.clear();
        mbWebsocket.dispose(true);
    }

    @Override
    public void handleRemoval() {
        storage.remove(config.email);
        authService = Optional.empty();
        super.handleRemoval();
    }

    /**
     * https://next.openhab.org/javadoc/latest/org/openhab/core/auth/client/oauth2/package-summary.html
     */
    @Override
    public void onAccessTokenResponse(AccessTokenResponse tokenResponse) {
        if (!Constants.NOT_SET.equals(tokenResponse.getAccessToken())) {
            scheduler.schedule(this::refresh, 2, TimeUnit.SECONDS);
        } else {
            // all failed - start manual authorization
            String textKey = Constants.STATUS_TEXT_PREFIX + thing.getThingTypeUID().getId()
                    + Constants.STATUS_AUTH_NEEDED;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, textKey);
        }
    }

    public String getWSUri() {
        return Utils.getWebsocketServer(config.region);
    }

    public ClientUpgradeRequest getClientUpgradeRequest() {
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setHeader("Authorization", authService.get().getToken());
        request.setHeader("X-SessionId", UUID.randomUUID().toString());
        request.setHeader("X-TrackingId", UUID.randomUUID().toString());
        request.setHeader("Ris-Os-Name", Constants.RIS_OS_NAME);
        request.setHeader("Ris-Os-Version", Constants.RIS_OS_VERSION);
        request.setHeader("Ris-Sdk-Version", Utils.getRisSDKVersion(config.region));
        request.setHeader("X-Locale",
                localeProvider.getLocale().getLanguage() + "-" + localeProvider.getLocale().getCountry()); // de-DE
        request.setHeader("User-Agent", Utils.getApplication(config.region));
        request.setHeader("X-Applicationname", Utils.getUserAgent(config.region));
        request.setHeader("Ris-Application-Version", Utils.getRisApplicationVersion(config.region));
        return request;
    }

    public void registerVin(String vin, VehicleHandler handler) {
        discoveryService.vehicleRemove(this, vin, handler.getThing().getThingTypeUID().getId());
        activeVehicleHandlerMap.put(vin, handler);
        discovery(vin); // update properties for added vehicle
        VEPUpdate updateForVin = vepUpdateMap.get(vin);
        if (updateForVin != null) {
            handler.enqueueUpdate(updateForVin);
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
                mbWebsocket.sendAcknowledgeMessage(cm);
            }
        } else if (pm.hasAssignedVehicles()) {
            for (int i = 0; i < pm.getAssignedVehicles().getVinsCount(); i++) {
                String vin = pm.getAssignedVehicles().getVins(i);
                discovery(vin);
            }
            AcknowledgeAssignedVehicles ack = AcknowledgeAssignedVehicles.newBuilder().build();
            ClientMessage cm = ClientMessage.newBuilder().setAcknowledgeAssignedVehicles(ack).build();
            mbWebsocket.sendAcknowledgeMessage(cm);
        } else if (pm.hasApptwinCommandStatusUpdatesByVin()) {
            AppTwinCommandStatusUpdatesByVIN csubv = pm.getApptwinCommandStatusUpdatesByVin();
            commandStatusUpdate(csubv.getUpdatesByVinMap());
            AcknowledgeAppTwinCommandStatusUpdatesByVIN ack = AcknowledgeAppTwinCommandStatusUpdatesByVIN.newBuilder()
                    .setSequenceNumber(csubv.getSequenceNumber()).build();
            ClientMessage cm = ClientMessage.newBuilder().setAcknowledgeApptwinCommandStatusUpdateByVin(ack).build();
            mbWebsocket.sendAcknowledgeMessage(cm);
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
        if (activeVehicleHandlerMap.containsKey(vin)) {
            VehicleHandler vh = activeVehicleHandlerMap.get(vin);
            Map<String, String> properties = getStringCapabilities(vin);
            properties.putAll(vh.getThing().getProperties());
            vh.getThing().setProperties(properties);
        } else {
            if (!capabilitiesMap.containsKey(vin)) {
                // only report new discovery if capabilities aren't discovered yet
                Map<String, Object> discoveryProperties = getCapabilities(vin);
                discoveryProperties.put("vin", vin);
                discoveryService.vehicleDiscovered(this, vin, discoveryProperties);
            }
        }
    }

    private Map<String, String> getStringCapabilities(String vin) {
        Map<String, Object> props = getCapabilities(vin);
        Map<String, String> stringProps = new HashMap<>();
        props.forEach((key, value) -> {
            stringProps.put(key, value.toString());
        });
        return stringProps;
    }

    private Map<String, Object> getCapabilities(String vin) {
        // check cache before hammering API
        Map<String, Object> m = capabilitiesMap.get(vin);
        if (m != null) {
            return m;
        }
        Map<String, Object> featureMap = new HashMap<>();
        try {
            // add vehicle capabilities
            String capabilitiesUrl = Utils.getRestAPIServer(config.region) + String.format(capabilitiesEndpoint, vin);
            Request capabilitiesRequest = httpClient.newRequest(capabilitiesUrl);
            authService.get().addBasicHeaders(capabilitiesRequest);
            capabilitiesRequest.header("X-SessionId", UUID.randomUUID().toString());
            capabilitiesRequest.header("X-TrackingId", UUID.randomUUID().toString());
            capabilitiesRequest.header("Authorization", authService.get().getToken());

            ContentResponse capabilitiesResponse = capabilitiesRequest
                    .timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            String featureCapabilitiesJsonString = capabilitiesResponse.getContentAsString();
            if (!storage.containsKey(vin + FEATURE_APPENDIX)) {
                storage.put(vin + FEATURE_APPENDIX, featureCapabilitiesJsonString);
            }

            JSONObject jsonResponse = new JSONObject(featureCapabilitiesJsonString);
            JSONObject features = jsonResponse.getJSONObject("features");
            features.keySet().forEach(key -> {
                String value = features.get(key).toString();
                String newKey = Character.toUpperCase(key.charAt(0)) + key.substring(1);
                newKey = "feature" + newKey;
                featureMap.put(newKey, value);
            });

            // get vehicle type
            JSONObject vehicle = jsonResponse.getJSONObject("vehicle");
            JSONArray fuelTypes = vehicle.getJSONArray("fuelTypes");
            if (fuelTypes.length() > 1) {
                featureMap.put("vehicle", Constants.HYBRID);
            } else if ("ELECTRIC".equals(fuelTypes.get(0))) {
                featureMap.put("vehicle", Constants.BEV);
            } else {
                featureMap.put("vehicle", Constants.COMBUSTION);
            }

            // add command capabilities
            String commandCapabilitiesUrl = Utils.getRestAPIServer(config.region)
                    + String.format(commandCapabilitiesEndpoint, vin);
            Request commandCapabilitiesRequest = httpClient.newRequest(commandCapabilitiesUrl);
            authService.get().addBasicHeaders(commandCapabilitiesRequest);
            commandCapabilitiesRequest.header("X-SessionId", UUID.randomUUID().toString());
            commandCapabilitiesRequest.header("X-TrackingId", UUID.randomUUID().toString());
            commandCapabilitiesRequest.header("Authorization", authService.get().getToken());
            ContentResponse commandCapabilitiesResponse = commandCapabilitiesRequest
                    .timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();

            String commandCapabilitiesJsonString = commandCapabilitiesResponse.getContentAsString();
            if (!storage.containsKey(vin + COMMAND_APPENDIX)) {
                storage.put(vin + COMMAND_APPENDIX, commandCapabilitiesJsonString);
            }
            JSONObject commands = new JSONObject(commandCapabilitiesJsonString);
            JSONArray commandArray = commands.getJSONArray("commands");
            commandArray.forEach(object -> {
                String commandName = ((JSONObject) object).get("commandName").toString();
                String[] words = commandName.split("[\\W_]+");
                StringBuilder builder = new StringBuilder();
                builder.append("command");
                for (int i = 0; i < words.length; i++) {
                    String word = words[i];
                    word = word.isEmpty() ? word
                            : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
                    builder.append(word);
                }
                String value = ((JSONObject) object).get("isAvailable").toString();
                featureMap.put(builder.toString(), value);
            });
            // store in cache
            capabilitiesMap.put(vin, featureMap);
            return featureMap;
        } catch (InterruptedException | TimeoutException | ExecutionException | JSONException e) {
            logger.trace("Error retrieving capabilities: {}", e.getMessage());
            featureMap.clear();
        }
        return featureMap;
    }

    public void sendCommand(@Nullable ClientMessage cm) {
        if (cm != null) {
            mbWebsocket.addCommand(cm);
        }
        scheduler.schedule(this::refresh, 2, TimeUnit.SECONDS);
    }

    public void keepAlive(boolean b) {
        mbWebsocket.keepAlive(b);
    }

    @Override
    public void updateStatus(ThingStatus ts) {
        super.updateStatus(ts);
    }

    @Override
    public void updateStatus(ThingStatus ts, ThingStatusDetail tsd, @Nullable String tsdt) {
        super.updateStatus(ts, tsd, tsdt);
    }

    /**
     * Vehicle Actions
     *
     * @param poi
     */

    public void sendPoi(String vin, JSONObject poi) {
        String poiUrl = Utils.getRestAPIServer(config.region) + String.format(poiEndpoint, vin);
        Request poiRequest = httpClient.POST(poiUrl);
        authService.get().addBasicHeaders(poiRequest);
        poiRequest.header("X-SessionId", UUID.randomUUID().toString());
        poiRequest.header("X-TrackingId", UUID.randomUUID().toString());
        poiRequest.header("Authorization", authService.get().getToken());
        poiRequest.header(HttpHeader.CONTENT_TYPE, "application/json");
        poiRequest.content(new StringContentProvider(poi.toString(), "utf-8"));

        try {
            ContentResponse cr = poiRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            logger.trace("Send POI Response {} : {}", cr.getStatus(), cr.getContentAsString());
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.trace("Error Sending POI {}", e.getMessage());
        }
    }
}
