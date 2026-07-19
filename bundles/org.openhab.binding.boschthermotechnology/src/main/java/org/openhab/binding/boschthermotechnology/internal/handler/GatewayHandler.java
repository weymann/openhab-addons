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
package org.openhab.binding.boschthermotechnology.internal.handler;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_DHW_CHARGE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_DHW_CHARGE_DURATION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_DHW_OPERATION_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_DHW_REDUCE_TEMP_ON_ALARM;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_DHW_SINGLE_CHARGE_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HC_MANUAL_ROOM_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HEAT_SOURCE_CH_STATUS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_NOTIFICATIONS_ACTIVE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_FIRMWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_HARDWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_SERIAL_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CHARGE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CHARGE_DURATION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_OPERATION_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_REDUCE_TEMP_ON_ALARM;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_SINGLE_CHARGE_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_FIRMWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_HARDWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_SERIAL_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_CH_STATUS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_NUMBER_OF_STARTS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_RETURN_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.MANUAL_ROOM_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.NOTIFICATIONS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.OUTDOOR_TEMPERATURE;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;
import org.openhab.binding.boschthermotechnology.internal.config.GatewayConfiguration;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * The {@link GatewayHandler} represents a single Bosch/Buderus heating gateway. It polls the
 * PointT resource paths confirmed by the reverse-engineering analysis (see
 * {@code BoschThermotechnologyBindingConstants.ResourcePaths}) on a fixed delay and forwards
 * channel commands to writable resources.
 *
 * <p>
 * Static gateway metadata (serial id, firmware/hardware version) is exposed as Thing properties,
 * never as channels, and is refreshed on its own daily schedule - independent of the regular data
 * poll - since it changes at most on a firmware update, not every {@code refreshInterval}.
 * Heat source status and notifications are exposed as raw String channels because their exact
 * value shape has not yet been confirmed against a live gateway - see the corresponding
 * channel-type TODOs in {@code thing-types.xml}. Current DHW temperature and heating circuit room
 * temperature are still not available - their resource paths were not confirmed in either source
 * analysis this binding is based on.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GatewayHandler extends BaseThingHandler {

    /** How often static gateway metadata (serial id, firmware/hardware version) is refreshed. */
    private static final long PROPERTIES_REFRESH_INTERVAL_DAYS = 1;

    /**
     * Bidirectional mapping between the {@code dhw-operation-mode} channel's Number codes (see the
     * {@code option} values in {@code thing-types.xml}) and the String values the PointT API
     * actually reports/expects at {@code /dhwCircuits/dhw1/operationMode}. The channel is declared
     * as Number (rather than String, as originally implemented) so it can be used with Number-based
     * UI widgets/rules; the codes below are a binding-internal convention only, not part of the API.
     */
    private static final Map<Integer, String> DHW_OPERATION_MODE_TO_API_VALUE = Map.of(0, "Off", 1, "Eco+", 2, "Eco", 3,
            "Comfort", 4, "Auto");
    private static final Map<String, Integer> DHW_OPERATION_MODE_TO_CODE = DHW_OPERATION_MODE_TO_API_VALUE.entrySet()
            .stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private final Logger logger = LoggerFactory.getLogger(GatewayHandler.class);

    private @Nullable GatewayConfiguration config;
    private @Nullable ScheduledFuture<?> pollingFuture;
    private @Nullable ScheduledFuture<?> propertiesFuture;

    public GatewayHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        GatewayConfiguration currentConfig = getConfigAs(GatewayConfiguration.class);
        config = currentConfig;

        if (currentConfig.gatewayId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "gatewayId must not be empty");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
        pollingFuture = scheduler.scheduleWithFixedDelay(this::poll, 0, currentConfig.refreshInterval,
                TimeUnit.SECONDS);
        // Runs independently of the data poll above, on its own much longer cadence - this is
        // metadata, not live telemetry, and must not add load to (or depend on the timing of) the
        // regular polling cycle.
        propertiesFuture = scheduler.scheduleWithFixedDelay(this::fetchGatewayProperties, 0,
                PROPERTIES_REFRESH_INTERVAL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> future = pollingFuture;
        if (future != null) {
            future.cancel(true);
        }
        pollingFuture = null;

        ScheduledFuture<?> propsFuture = propertiesFuture;
        if (propsFuture != null) {
            propsFuture.cancel(true);
        }
        propertiesFuture = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::poll);
            return;
        }

        String resourcePath = resourcePathForChannel(channelUID.getId());
        if (resourcePath == null) {
            logger.debug("Ignoring command for read-only or unknown channel {}", channelUID.getId());
            return;
        }

        JsonElement jsonValue;
        try {
            jsonValue = toJsonValue(channelUID.getId(), command);
        } catch (IllegalArgumentException e) {
            logger.warn("Cannot map command {} for channel {}: {}", command, channelUID.getId(), e.getMessage());
            return;
        }

        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        GatewayConfiguration currentConfig = config;
        if (bridgeHandler == null || currentConfig == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        try {
            String accessToken = bridgeHandler.getValidAccessToken();
            bridgeHandler.getApiClient().putResource(accessToken, currentConfig.gatewayId, resourcePath, jsonValue);
        } catch (PointTAuthException e) {
            bridgeHandler.invalidateAccessToken();
            logger.debug("Access token rejected while writing {}: {}", resourcePath, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (PointTApiException e) {
            logger.warn("Could not write resource {}: {}", resourcePath, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void poll() {
        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        GatewayConfiguration currentConfig = config;
        if (bridgeHandler == null || currentConfig == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        try {
            String accessToken = bridgeHandler.getValidAccessToken();
            PointTApiClient apiClient = bridgeHandler.getApiClient();
            String gatewayId = currentConfig.gatewayId;

            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE,
                    OUTDOOR_TEMPERATURE, this::toTemperatureState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HC_MANUAL_ROOM_SETPOINT,
                    MANUAL_ROOM_SETPOINT, this::toTemperatureState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_DHW_CHARGE_DURATION,
                    DHW_CHARGE_DURATION, this::toMinutesState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_DHW_SINGLE_CHARGE_SETPOINT,
                    DHW_SINGLE_CHARGE_SETPOINT, this::toTemperatureState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_DHW_OPERATION_MODE, DHW_OPERATION_MODE,
                    this::toDhwOperationModeState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_DHW_CHARGE, DHW_CHARGE,
                    this::toOnOffState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_DHW_REDUCE_TEMP_ON_ALARM,
                    DHW_REDUCE_TEMP_ON_ALARM, this::toOnOffState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HEAT_SOURCE_CH_STATUS,
                    HEAT_SOURCE_CH_STATUS, this::toRawStringState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE,
                    HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE, this::toTemperatureState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE,
                    HEAT_SOURCE_RETURN_TEMPERATURE, this::toTemperatureState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS,
                    HEAT_SOURCE_NUMBER_OF_STARTS, this::toCountState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM,
                    HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM, this::toHoursState);
            updateChannelFromResource(apiClient, accessToken, gatewayId, CHANNEL_NOTIFICATIONS_ACTIVE, NOTIFICATIONS,
                    this::toRawStringState);

            updateStatus(ThingStatus.ONLINE);
        } catch (PointTAuthException e) {
            bridgeHandler.invalidateAccessToken();
            logger.debug("Access token rejected during poll: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (PointTApiException e) {
            logger.debug("Poll failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Fetches static gateway metadata (serial id, firmware/hardware version) and stores it as Thing
     * properties. Runs on its own {@link #PROPERTIES_REFRESH_INTERVAL_DAYS}-based schedule, entirely
     * independent of {@link #poll()} - unlike that method, a failure here is logged only and never
     * changes {@link ThingStatus}, since this is auxiliary metadata and the regular data poll is the
     * sole authority on whether the Thing is ONLINE/OFFLINE.
     */
    private void fetchGatewayProperties() {
        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        GatewayConfiguration currentConfig = config;
        if (bridgeHandler == null || currentConfig == null) {
            logger.debug("Skipping gateway properties refresh - bridge or configuration not ready yet");
            return;
        }

        try {
            String accessToken = bridgeHandler.getValidAccessToken();
            PointTApiClient apiClient = bridgeHandler.getApiClient();
            String gatewayId = currentConfig.gatewayId;

            Map<String, String> properties = new HashMap<>();
            putResourceValueAsString(properties, PROPERTY_SERIAL_ID,
                    apiClient.getResource(accessToken, gatewayId, GATEWAY_SERIAL_ID).value);
            putResourceValueAsString(properties, PROPERTY_FIRMWARE_VERSION,
                    apiClient.getResource(accessToken, gatewayId, GATEWAY_FIRMWARE_VERSION).value);
            putResourceValueAsString(properties, PROPERTY_HARDWARE_VERSION,
                    apiClient.getResource(accessToken, gatewayId, GATEWAY_HARDWARE_VERSION).value);
            if (!properties.isEmpty()) {
                updateProperties(properties);
            }
        } catch (PointTAuthException e) {
            bridgeHandler.invalidateAccessToken();
            logger.debug("Access token rejected while refreshing gateway properties: {}", e.getMessage());
        } catch (PointTApiException e) {
            // Will simply retry on the next scheduled run - this is metadata, not a channel update,
            // so it must not affect ThingStatus, which the regular data poll owns exclusively.
            logger.debug("Could not refresh gateway properties, will retry on next scheduled run: {}", e.getMessage());
        }
    }

    private void putResourceValueAsString(Map<String, String> properties, String propertyKey,
            @Nullable JsonElement value) {
        if (value != null && value.isJsonPrimitive()) {
            properties.put(propertyKey, value.getAsString());
        }
    }

    private void updateChannelFromResource(PointTApiClient apiClient, String accessToken, String gatewayId,
            String channelId, String resourcePath, Function<JsonElement, State> stateMapper) throws PointTApiException {
        ResourceDto resource = apiClient.getResource(accessToken, gatewayId, resourcePath);
        JsonElement value = resource.value;
        updateState(channelId, value == null ? UnDefType.NULL : stateMapper.apply(value));
    }

    private State toTemperatureState(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), SIUnits.CELSIUS);
    }

    private State toMinutesState(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.MINUTE);
    }

    private State toHoursState(JsonElement value) {
        return new QuantityType<>(value.getAsDouble(), Units.HOUR);
    }

    private State toCountState(JsonElement value) {
        return new DecimalType(value.getAsDouble());
    }

    /**
     * Maps the PointT API's DHW operation mode String value onto the {@code dhw-operation-mode}
     * channel's Number code, per {@link #DHW_OPERATION_MODE_TO_CODE}. An unrecognized value is
     * logged and reported as {@link UnDefType#UNDEF} rather than failing the whole poll.
     */
    private State toDhwOperationModeState(JsonElement value) {
        Integer code = DHW_OPERATION_MODE_TO_CODE.get(value.getAsString());
        if (code == null) {
            logger.debug("Unknown DHW operation mode value '{}' reported by gateway", value.getAsString());
            return UnDefType.UNDEF;
        }
        return new DecimalType(code);
    }

    /**
     * Serializes any resource value to a String channel, regardless of its JSON shape. Used for
     * resources whose exact value type is not yet confirmed against a live gateway (heat source
     * status, notifications) - see the corresponding channel-type TODOs in {@code thing-types.xml}.
     */
    private State toRawStringState(JsonElement value) {
        return new StringType(value.isJsonPrimitive() ? value.getAsString() : value.toString());
    }

    private State toOnOffState(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return OnOffType.from(value.getAsBoolean());
        }
        // TODO ($Dev): confirm whether the gateway actually reports these as booleans or as 0/1
        // numbers before release - handling both defensively for now.
        return OnOffType.from(value.getAsInt() != 0);
    }

    private JsonElement toJsonValue(String channelId, Command command) {
        if (CHANNEL_DHW_OPERATION_MODE.equals(channelId)) {
            if (!(command instanceof DecimalType decimal)) {
                throw new IllegalArgumentException(
                        "Unsupported command type " + command.getClass().getSimpleName() + " for " + channelId);
            }
            String apiValue = DHW_OPERATION_MODE_TO_API_VALUE.get(decimal.intValue());
            if (apiValue == null) {
                throw new IllegalArgumentException("Unknown DHW operation mode code " + decimal.intValue());
            }
            return new JsonPrimitive(apiValue);
        }
        if (command instanceof QuantityType<?> quantity) {
            return new JsonPrimitive(quantity.doubleValue());
        }
        if (command instanceof DecimalType decimal) {
            return new JsonPrimitive(decimal.doubleValue());
        }
        if (command instanceof OnOffType onOff) {
            return new JsonPrimitive(onOff == OnOffType.ON);
        }
        if (command instanceof StringType stringType) {
            return new JsonPrimitive(stringType.toString());
        }
        throw new IllegalArgumentException("Unsupported command type " + command.getClass().getSimpleName());
    }

    private @Nullable String resourcePathForChannel(String channelId) {
        Map<String, String> writableChannelsToResourcePaths = Map.of(CHANNEL_HC_MANUAL_ROOM_SETPOINT,
                MANUAL_ROOM_SETPOINT, CHANNEL_DHW_CHARGE_DURATION, DHW_CHARGE_DURATION,
                CHANNEL_DHW_SINGLE_CHARGE_SETPOINT, DHW_SINGLE_CHARGE_SETPOINT, CHANNEL_DHW_OPERATION_MODE,
                DHW_OPERATION_MODE, CHANNEL_DHW_CHARGE, DHW_CHARGE, CHANNEL_DHW_REDUCE_TEMP_ON_ALARM,
                DHW_REDUCE_TEMP_ON_ALARM);
        return writableChannelsToResourcePaths.get(channelId);
    }

    private @Nullable AccountBridgeHandler getAccountBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return bridge.getHandler() instanceof AccountBridgeHandler handler ? handler : null;
    }
}
