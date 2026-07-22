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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_PV_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_PV_INVERTER_INFO;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SOLAR_COLLECTOR_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_SOLAR_YIELD;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.PV_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.PV_INVERTER_INFO;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SOLAR_CIRCUIT_ID_DEFAULT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SOLAR_COLLECTOR_TEMPERATURE_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SOLAR_YIELD_TEMPLATE;

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
 * The {@link PvHandler} represents the photovoltaic inverter and solar-thermal collector readings
 * of one gateway - see ADR-005 for why these two technically distinct systems are grouped into
 * one thing (kept as two separately labeled channel groups, {@code photovoltaic} and
 * {@code solar-thermal}, so they are not conflated in the UI).
 *
 * <p>
 * Assumes a single solar-circuit instance ({@link SubThingConfiguration}, no per-circuit dynamic
 * channels like {@code HeatpumpHandler}) - see {@code SOLAR_CIRCUIT_ID_DEFAULT}'s Javadoc for why.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PvHandler extends BaseThingHandler {

    private @Nullable ResourcePollingSupport pollingSupport;

    public PvHandler(Thing thing) {
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
                ChannelResourceBinding.readWrite("photovoltaic#" + CHANNEL_PV_ENABLED, PV_ENABLED, StateMappers::onOff,
                        CommandWriters::onOff),
                ChannelResourceBinding.readOnly("photovoltaic#" + CHANNEL_PV_INVERTER_INFO, PV_INVERTER_INFO,
                        StateMappers::rawString),
                ChannelResourceBinding.readOnly("solar-thermal#" + CHANNEL_SOLAR_COLLECTOR_TEMPERATURE,
                        SOLAR_COLLECTOR_TEMPERATURE_TEMPLATE.formatted(SOLAR_CIRCUIT_ID_DEFAULT),
                        StateMappers::temperature),
                ChannelResourceBinding.readOnly("solar-thermal#" + CHANNEL_SOLAR_YIELD,
                        SOLAR_YIELD_TEMPLATE.formatted(SOLAR_CIRCUIT_ID_DEFAULT), StateMappers::energy));

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
