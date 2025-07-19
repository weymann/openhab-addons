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
package org.openhab.binding.mercedesme.internal.api;

import static org.openhab.binding.mercedesme.internal.Constants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.exception.MercedesMeException;
import org.openhab.binding.mercedesme.internal.utils.Utils;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.daimler.mbcarkit.proto.VehicleEvents;
import com.daimler.mbcarkit.proto.VehicleEvents.VEPUpdate;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * {@link RestApi} for single calls towards Mercedes servers
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class RestApi extends Authorization {
    private final Logger logger = LoggerFactory.getLogger(RestApi.class);

    private String commandCapabilitiesEndpoint = "/v1/vehicle/%s/capabilities/commands";
    private String vehicleAttributesEndpoint = "/v1/vehicle/%s/vehicleattributes";
    private String capabilitiesEndpoint = "/v1/vehicle/%s/capabilities";
    private String poiEndpoint = "/v1/vehicle/%s/route";

    public RestApi(AccessTokenRefreshListener atrl, HttpClient hc, AccountConfiguration ac, LocaleProvider l,
            Storage<String> store) {
        super(atrl, hc, ac, l, store);
    }

    public void restSendPoi(String vin, JSONObject poi) {
        String poiUrl = Utils.getRestAPIServer(config.region) + String.format(poiEndpoint, vin);
        Request poiRequest = httpClient.POST(poiUrl);
        addBasicHeaders(poiRequest);
        poiRequest.header("X-SessionId", UUID.randomUUID().toString());
        poiRequest.header("X-TrackingId", UUID.randomUUID().toString());
        poiRequest.header("Authorization", getToken());
        poiRequest.header(HttpHeader.CONTENT_TYPE, "application/json");
        poiRequest.content(new StringContentProvider(poi.toString(), "utf-8"));

        try {
            ContentResponse cr = poiRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            logger.trace("Send POI Response {} : {}", cr.getStatus(), cr.getContentAsString());
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.trace("Error Sending POI {}", e.getMessage());
        }
    }

    public VEPUpdate restGetVehicleAttributes(String vin) throws MercedesMeException {
        String vehicleUrl = Utils.getWidgetServer(config.region) + String.format(vehicleAttributesEndpoint, vin);
        logger.trace("Pull update {}", vehicleUrl);
        Request vehicleRequest = httpClient.newRequest(vehicleUrl);
        addBasicHeaders(vehicleRequest);
        vehicleRequest.header("X-SessionId", UUID.randomUUID().toString());
        vehicleRequest.header("X-TrackingId", UUID.randomUUID().toString());
        vehicleRequest.header("Authorization", getToken());

        String reason = "unknown";
        try {
            ContentResponse vehicleResponse = vehicleRequest
                    .timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            if (vehicleResponse.getStatus() == 200) {
                VEPUpdate update = VehicleEvents.VEPUpdate.parseFrom(vehicleResponse.getContent());
                return update;
            }
            reason = Integer.toString(vehicleResponse.getStatus());
            throw new MercedesMeException(reason);
        } catch (InterruptedException | TimeoutException | ExecutionException | InvalidProtocolBufferException e) {
            reason = e.getMessage();
            throw new MercedesMeException(reason == null ? "unknown" : reason);
        }
    }

    public Map<String, Object> restGetCapabilities(String vin) {
        logger.trace("Get capabilities for VIN {}", vin);
        Map<String, Object> featureMap = new HashMap<>();
        try {
            // add vehicle capabilities
            String capabilitiesUrl = Utils.getRestAPIServer(config.region) + String.format(capabilitiesEndpoint, vin);
            Request capabilitiesRequest = httpClient.newRequest(capabilitiesUrl);
            addBasicHeaders(capabilitiesRequest);
            capabilitiesRequest.header("X-SessionId", UUID.randomUUID().toString());
            capabilitiesRequest.header("X-TrackingId", UUID.randomUUID().toString());
            capabilitiesRequest.header("Authorization", getToken());

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
            addBasicHeaders(commandCapabilitiesRequest);
            commandCapabilitiesRequest.header("X-SessionId", UUID.randomUUID().toString());
            commandCapabilitiesRequest.header("X-TrackingId", UUID.randomUUID().toString());
            commandCapabilitiesRequest.header("Authorization", getToken());
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
            return featureMap;
        } catch (InterruptedException | TimeoutException | ExecutionException | JSONException e) {
            logger.trace("Error retrieving capabilities: {}", e.getMessage());
            featureMap.clear();
        }
        return featureMap;
    }
}
