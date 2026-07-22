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
package org.openhab.binding.boschthermotechnology.internal.handler.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;
import org.openhab.binding.boschthermotechnology.internal.dto.ResourceDto;
import org.openhab.binding.boschthermotechnology.internal.handler.PointTAccessProvider;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

/**
 * The {@link ResourcePollingSupport} class implements the poll-loop / write-command logic every
 * one of this binding's nine handlers needs (the {@code gateway} bridge and its eight ADR-005
 * child things), driven declaratively by a {@link List} of {@link ChannelResourceBinding}s
 * supplied by the handler.
 *
 * <p>
 * This logic previously existed only once, hardcoded into {@code GatewayHandler}. ADR-006 factors
 * it out via composition rather than a shared abstract base class, because {@code GatewayHandler}
 * (a {@code BaseBridgeHandler}) and the eight child handlers (plain {@code BaseThingHandler}s)
 * cannot share a common handler superclass in openHAB - each handler constructs one instance of
 * this class in {@code initialize()} and delegates {@code handleCommand()}/{@code dispose()} to
 * it, passing method references to its own (inherited) {@code updateState}/{@code updateStatus}.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ResourcePollingSupport {

    private final Logger logger = LoggerFactory.getLogger(ResourcePollingSupport.class);

    private final BiConsumer<String, State> stateUpdater;
    private final ThingStatusUpdater statusUpdater;
    private final PointTAccessProvider accessProvider;
    private final String gatewayId;
    private final List<ChannelResourceBinding> bindings;
    private final Map<String, ChannelResourceBinding> bindingsByChannelId;

    private @Nullable ScheduledFuture<?> pollingFuture;

    public ResourcePollingSupport(BiConsumer<String, State> stateUpdater, ThingStatusUpdater statusUpdater,
            PointTAccessProvider accessProvider, String gatewayId, List<ChannelResourceBinding> bindings) {
        this.stateUpdater = stateUpdater;
        this.statusUpdater = statusUpdater;
        this.accessProvider = accessProvider;
        this.gatewayId = gatewayId;
        this.bindings = List.copyOf(bindings);

        Map<String, ChannelResourceBinding> byChannelId = new HashMap<>();
        for (ChannelResourceBinding binding : this.bindings) {
            byChannelId.put(binding.channelId(), binding);
        }
        this.bindingsByChannelId = Map.copyOf(byChannelId);
    }

    /**
     * Starts polling on the given scheduler, cancelling any previously started polling first.
     * Mirrors the {@code scheduler.scheduleWithFixedDelay(this::poll, 0, refreshInterval,
     * TimeUnit.SECONDS)} call every handler made individually before ADR-006.
     */
    public void start(ScheduledExecutorService scheduler, int refreshIntervalSeconds) {
        stop();
        pollingFuture = scheduler.scheduleWithFixedDelay(this::poll, 0, refreshIntervalSeconds, TimeUnit.SECONDS);
    }

    /** Cancels polling. Call from the handler's own {@code dispose()}. */
    public void stop() {
        ScheduledFuture<?> future = pollingFuture;
        if (future != null) {
            future.cancel(true);
        }
        pollingFuture = null;
    }

    /**
     * Reads every bound resource and updates the corresponding channel state. Public so a handler
     * can also trigger an immediate out-of-cycle poll (e.g. {@code scheduler.execute(this::poll)}
     * from a {@code RefreshType} command), exactly as {@code GatewayHandler} did before ADR-006.
     */
    public void poll() {
        try {
            String accessToken = accessProvider.getValidAccessToken();
            PointTApiClient apiClient = accessProvider.getApiClient();
            for (ChannelResourceBinding binding : bindings) {
                ResourceDto resource = apiClient.getResource(accessToken, gatewayId, binding.resourcePath());
                JsonElement value = resource.value;
                stateUpdater.accept(binding.channelId(),
                        value == null ? UnDefType.NULL : binding.stateReader().apply(value));
            }
            statusUpdater.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);
        } catch (PointTAuthException e) {
            accessProvider.invalidateAccessToken();
            logger.debug("Access token rejected during poll: {}", e.getMessage());
            statusUpdater.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (PointTApiException e) {
            logger.debug("Poll failed: {}", e.getMessage());
            statusUpdater.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Writes a command to its bound resource, if the channel is known and writable. Call from the
     * handler's own {@code handleCommand(ChannelUID, Command)} for anything that is not a
     * {@code RefreshType} (handlers keep handling {@code RefreshType} themselves, since that maps
     * to {@link #poll()}, not a write).
     */
    public void handleCommand(String channelId, Command command) {
        ChannelResourceBinding binding = bindingsByChannelId.get(channelId);
        if (binding == null) {
            logger.debug("Ignoring command for unknown channel {}", channelId);
            return;
        }

        Function<Command, JsonElement> writer = binding.commandWriter();
        if (writer == null) {
            logger.debug("Ignoring command for read-only channel {}", channelId);
            return;
        }

        JsonElement jsonValue;
        try {
            jsonValue = writer.apply(command);
        } catch (IllegalArgumentException e) {
            logger.warn("Cannot map command {} for channel {}: {}", command, channelId, e.getMessage());
            return;
        }

        try {
            String accessToken = accessProvider.getValidAccessToken();
            accessProvider.getApiClient().putResource(accessToken, gatewayId, binding.resourcePath(), jsonValue);
        } catch (PointTAuthException e) {
            accessProvider.invalidateAccessToken();
            logger.debug("Access token rejected while writing {}: {}", binding.resourcePath(), e.getMessage());
            statusUpdater.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (PointTApiException e) {
            logger.warn("Could not write resource {}: {}", binding.resourcePath(), e.getMessage());
            statusUpdater.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }
}
