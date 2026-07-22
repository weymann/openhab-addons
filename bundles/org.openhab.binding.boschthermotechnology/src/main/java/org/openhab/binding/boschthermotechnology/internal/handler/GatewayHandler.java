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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_NOTIFICATIONS_ACTIVE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_AWAY_MODE_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_HOLIDAY_MODE_ACTIVE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_SEASON_OPTIMIZER_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SYSTEM_SILENT_MODE_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_FIRMWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_HARDWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.PROPERTY_SERIAL_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.AWAY_MODE_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_FIRMWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_HARDWARE_VERSION;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.GATEWAY_SERIAL_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HOLIDAY_MODE_ACTIVE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.NOTIFICATIONS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.OUTDOOR_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SEASON_OPTIMIZER_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SILENT_MODE_ENABLED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;
import org.openhab.binding.boschthermotechnology.internal.config.GatewayConfiguration;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ChannelResourceBinding;
import org.openhab.binding.boschthermotechnology.internal.handler.support.CommandWriters;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ResourcePollingSupport;
import org.openhab.binding.boschthermotechnology.internal.handler.support.StateMappers;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

/**
 * The {@link GatewayHandler} represents a single Bosch/Buderus gateway (one entry from
 * {@code GET /gateways/}). Since ADR-005/ADR-006 it is a bridge: it only owns channels that
 * genuinely belong to the physical box itself (outdoor temperature, away mode, silent mode,
 * season optimizer, active holiday mode, notifications), and acts as the discovery parent for the
 * eight sibling child thing-types (heat pump, PV, pool, ventilation zone, zone thermostats, energy
 * monitoring, AC unit, water softener) that used to be bundled into this one Thing's fixed channel
 * set.
 *
 * <p>
 * Static gateway metadata (serial id, firmware/hardware version) is exposed as Thing properties,
 * never as channels, and is refreshed on its own daily schedule - independent of the regular data
 * poll - since it changes at most on a firmware update, not every {@code refreshInterval}. Several
 * channels are exposed as raw String channels because their exact value shape has not yet been
 * confirmed against a live gateway - see the corresponding channel-type TODOs in
 * {@code thing-types.xml}.
 *
 * <p>
 * Implements {@link PointTAccessProvider} by delegating to its own parent
 * {@code AccountBridgeHandler} - a {@code gateway} has no access token of its own, the token is
 * account-scoped. This lets every child handler depend on {@code GatewayHandler} exactly the same
 * way {@code GatewayHandler} itself depends on {@code AccountBridgeHandler}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GatewayHandler extends BaseBridgeHandler implements PointTAccessProvider {

    /** How often static gateway metadata (serial id, firmware/hardware version) is refreshed. */
    private static final long PROPERTIES_REFRESH_INTERVAL_DAYS = 1;

    private final Logger logger = LoggerFactory.getLogger(GatewayHandler.class);

    private @Nullable GatewayConfiguration config;
    private @Nullable ResourcePollingSupport pollingSupport;
    private @Nullable ScheduledFuture<?> propertiesFuture;
    private @Nullable Runnable childDiscoveryScanTrigger;

    public GatewayHandler(Bridge bridge) {
        super(bridge);
    }

    /**
     * Wires up the callback that triggers a {@code ChildThingDiscoveryService} scan. Set once by
     * {@code BoschThermotechnologyHandlerFactory} right after construction, mirroring
     * {@code AccountBridgeHandler.setDiscoveryScanTrigger(...)} (ADR-004) one level down.
     */
    public void setChildDiscoveryScanTrigger(Runnable childDiscoveryScanTrigger) {
        this.childDiscoveryScanTrigger = childDiscoveryScanTrigger;
    }

    private void triggerChildDiscoveryScan() {
        Runnable trigger = childDiscoveryScanTrigger;
        if (trigger != null) {
            scheduler.execute(trigger);
        }
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

        ResourcePollingSupport support = new ResourcePollingSupport(this::updateState, this::updateStatus, this,
                currentConfig.gatewayId, systemChannelBindings());
        pollingSupport = support;
        support.start(scheduler, currentConfig.refreshInterval);

        propertiesFuture = scheduler.scheduleWithFixedDelay(this::fetchGatewayProperties, 0,
                PROPERTIES_REFRESH_INTERVAL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void dispose() {
        ResourcePollingSupport support = pollingSupport;
        if (support != null) {
            support.stop();
        }
        pollingSupport = null;

        ScheduledFuture<?> propsFuture = propertiesFuture;
        if (propsFuture != null) {
            propsFuture.cancel(true);
        }
        propertiesFuture = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ResourcePollingSupport support = pollingSupport;
        if (support == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR);
            return;
        }
        if (command instanceof RefreshType) {
            scheduler.execute(support::poll);
            return;
        }
        support.handleCommand(channelUID.getId(), command);
    }

    /**
     * Once this gateway bridge transitions to {@code ONLINE}, also scan for its child things -
     * mirroring {@code AccountBridgeHandler}'s automatic scan-on-online behavior (ADR-004) one
     * level down, so heat pump/PV/pool/... things appear without a manual Inbox step. Only fires
     * on the actual transition (not every already-ONLINE poll cycle), by comparing against the
     * status before this call.
     */
    @Override
    protected void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        boolean wasOnline = thing.getStatus() == ThingStatus.ONLINE;
        super.updateStatus(status, statusDetail, description);
        if (status == ThingStatus.ONLINE && !wasOnline) {
            triggerChildDiscoveryScan();
        }
    }

    private List<ChannelResourceBinding> systemChannelBindings() {
        return List.of(
                ChannelResourceBinding.readOnly(CHANNEL_SYSTEM_OUTDOOR_TEMPERATURE, OUTDOOR_TEMPERATURE,
                        StateMappers::temperature),
                ChannelResourceBinding.readWrite(CHANNEL_SYSTEM_AWAY_MODE_ENABLED, AWAY_MODE_ENABLED,
                        StateMappers::onOff, CommandWriters::onOff),
                ChannelResourceBinding.readWrite(CHANNEL_SYSTEM_SILENT_MODE_ENABLED, SILENT_MODE_ENABLED,
                        StateMappers::onOff, CommandWriters::onOff),
                ChannelResourceBinding.readWrite(CHANNEL_SYSTEM_SEASON_OPTIMIZER_MODE, SEASON_OPTIMIZER_MODE,
                        StateMappers::rawString, CommandWriters::string),
                ChannelResourceBinding.readOnly(CHANNEL_SYSTEM_HOLIDAY_MODE_ACTIVE, HOLIDAY_MODE_ACTIVE,
                        StateMappers::rawString),
                ChannelResourceBinding.readOnly(CHANNEL_NOTIFICATIONS_ACTIVE, NOTIFICATIONS, StateMappers::rawString));
    }

    /**
     * Fetches static gateway metadata (serial id, firmware/hardware version) and stores it as Thing
     * properties. Runs on its own {@link #PROPERTIES_REFRESH_INTERVAL_DAYS}-based schedule, entirely
     * independent of the regular data poll - unlike that poll, a failure here is logged only and
     * never changes {@link ThingStatus}, since this is auxiliary metadata.
     */
    private void fetchGatewayProperties() {
        GatewayConfiguration currentConfig = config;
        if (currentConfig == null) {
            logger.debug("Skipping gateway properties refresh - configuration not ready yet");
            return;
        }

        try {
            String accessToken = getValidAccessToken();
            PointTApiClient apiClient = getApiClient();
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
            invalidateAccessToken();
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

    // --- PointTAccessProvider, delegating to the account bridge - see class Javadoc ---

    @Override
    public PointTApiClient getApiClient() {
        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        if (bridgeHandler == null) {
            // Should not happen in practice: every call site fetches getValidAccessToken() first,
            // which throws a checked PointTApiException in this same situation - see below.
            throw new IllegalStateException("Account bridge handler not available");
        }
        return bridgeHandler.getApiClient();
    }

    @Override
    public String getValidAccessToken() throws PointTApiException {
        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        if (bridgeHandler == null) {
            throw new PointTApiException("Account bridge handler not available");
        }
        return bridgeHandler.getValidAccessToken();
    }

    @Override
    public void invalidateAccessToken() {
        AccountBridgeHandler bridgeHandler = getAccountBridgeHandler();
        if (bridgeHandler != null) {
            bridgeHandler.invalidateAccessToken();
        }
    }

    private @Nullable AccountBridgeHandler getAccountBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return bridge.getHandler() instanceof AccountBridgeHandler handler ? handler : null;
    }
}
