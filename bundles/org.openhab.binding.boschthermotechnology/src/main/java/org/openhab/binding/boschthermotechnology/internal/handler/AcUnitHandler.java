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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_AC_FAN_SPEED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_AC_OPERATION_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_AC_TEMPERATURE_SETPOINT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.AC_FAN_SPEED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.AC_OPERATION_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.AC_TEMPERATURE_SETPOINT;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.config.SubThingConfiguration;
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
 * The {@link AcUnitHandler} represents one room air-conditioning (RAC) unit - typically its own
 * paired gateway ({@code AirConditioner-*} {@code SystemType}) rather than a subsystem of a heat
 * pump gateway, see ADR-005/ADR-006.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AcUnitHandler extends BaseThingHandler {

    private @Nullable ResourcePollingSupport pollingSupport;

    public AcUnitHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        SubThingConfiguration config = getConfigAs(SubThingConfiguration.class);
        if (config.gatewayId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "gatewayId must not be empty");
            return;
        }

        GatewayHandler gatewayHandler = getGatewayHandler();
        if (gatewayHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        List<ChannelResourceBinding> bindings = List.of(
                ChannelResourceBinding.readWrite(CHANNEL_AC_OPERATION_MODE, AC_OPERATION_MODE, StateMappers::rawString,
                        CommandWriters::string),
                ChannelResourceBinding.readWrite(CHANNEL_AC_FAN_SPEED, AC_FAN_SPEED, StateMappers::rawString,
                        CommandWriters::string),
                ChannelResourceBinding.readWrite(CHANNEL_AC_TEMPERATURE_SETPOINT, AC_TEMPERATURE_SETPOINT,
                        StateMappers::temperature, CommandWriters::number));

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
