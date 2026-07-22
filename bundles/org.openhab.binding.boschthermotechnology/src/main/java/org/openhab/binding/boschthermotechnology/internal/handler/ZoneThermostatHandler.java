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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ZONE_AVERAGE_CURRENT_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ZONE_CHILD_LOCK;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ZONE_MANUAL_ROOM_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ZONE_AVERAGE_CURRENT_TEMPERATURE_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ZONE_CHILD_LOCK_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ZONE_MANUAL_ROOM_SETPOINT_TEMPLATE;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.config.ZoneThermostatConfiguration;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ChannelResourceBinding;
import org.openhab.binding.boschthermotechnology.internal.handler.support.CommandWriters;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ResourcePollingSupport;
import org.openhab.binding.boschthermotechnology.internal.handler.support.StateMappers;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

/**
 * The {@link ZoneThermostatHandler} represents one multi-zone RF room thermostat
 * ({@code zones/zone{n}}) - a subsystem structurally separate from the {@code heatingCircuits}
 * resource tree, see {@code myapp-api-analysis.md} and ADR-005.
 *
 * <p>
 * TODO ($Dev): the underlying setpoint/operation-mode resource paths are parameterized by a
 * heat/cool mode segment ({@code zones/zone{n}/{heatCoolMode}/...}); this handler hardcodes
 * {@code "heat"} - see the TODO on {@code zone-manual-room-setpoint} in {@code thing-types.xml}
 * for what confirming a {@code "cool"} variant would require.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ZoneThermostatHandler extends BaseThingHandler {

    private @Nullable ResourcePollingSupport pollingSupport;

    public ZoneThermostatHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        ZoneThermostatConfiguration config = getConfigAs(ZoneThermostatConfiguration.class);
        if (config.gatewayId.isBlank() || config.zoneId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "gatewayId and zoneId must not be empty");
            return;
        }

        GatewayHandler gatewayHandler = getGatewayHandler();
        if (gatewayHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        List<ChannelResourceBinding> bindings = List.of(
                ChannelResourceBinding.readWrite(CHANNEL_ZONE_MANUAL_ROOM_SETPOINT,
                        ZONE_MANUAL_ROOM_SETPOINT_TEMPLATE.formatted(config.zoneId), StateMappers::temperature,
                        CommandWriters::number),
                ChannelResourceBinding.readOnly(CHANNEL_ZONE_AVERAGE_CURRENT_TEMPERATURE,
                        ZONE_AVERAGE_CURRENT_TEMPERATURE_TEMPLATE.formatted(config.zoneId), StateMappers::temperature),
                ChannelResourceBinding.readWrite(CHANNEL_ZONE_CHILD_LOCK,
                        ZONE_CHILD_LOCK_TEMPLATE.formatted(config.zoneId), StateMappers::onOff, CommandWriters::onOff));

        ResourcePollingSupport support = new ResourcePollingSupport(this::updateState, this::updateStatus,
                gatewayHandler, config.gatewayId, bindings);
        pollingSupport = support;
        support.start(scheduler, config.refreshInterval);
    }

    @Override
    public void dispose() {
        ResourcePollingSupport support = pollingSupport;
        if (support != null) {
            support.stop();
        }
        pollingSupport = null;
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

    private @Nullable GatewayHandler getGatewayHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return bridge.getHandler() instanceof GatewayHandler handler ? handler : null;
    }
}
