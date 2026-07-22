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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ENERGY_ACTUAL_CH_POWER;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ENERGY_ACTUAL_DHW_POWER;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CHANNEL_ENERGY_TOTAL_CONSUMED_ENERGY;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ENERGY_ACTUAL_CH_POWER;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ENERGY_ACTUAL_DHW_POWER;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ENERGY_TOTAL_CONSUMED_ENERGY;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.config.SubThingConfiguration;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ChannelResourceBinding;
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
 * The {@link EnergyMonitoringHandler} represents the historical energy-monitoring recordings of
 * one gateway's heat source(s) - always proposed alongside {@code heatpump} since it depends on
 * the same heat source existing, see ADR-006.
 *
 * <p>
 * TODO ($Dev): confirm whether the {@code recordings/*} endpoints return a single scalar or a
 * time series ({@code YRecording}, per {@code myapp-api-analysis.md}) against a live gateway - see
 * the channel-type descriptions in {@code thing-types.xml}. All channels here are read-only
 * regardless.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EnergyMonitoringHandler extends BaseThingHandler {

    private @Nullable ResourcePollingSupport pollingSupport;

    public EnergyMonitoringHandler(Thing thing) {
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
                ChannelResourceBinding.readOnly(CHANNEL_ENERGY_ACTUAL_CH_POWER, ENERGY_ACTUAL_CH_POWER,
                        StateMappers::power),
                ChannelResourceBinding.readOnly(CHANNEL_ENERGY_ACTUAL_DHW_POWER, ENERGY_ACTUAL_DHW_POWER,
                        StateMappers::power),
                ChannelResourceBinding.readOnly(CHANNEL_ENERGY_TOTAL_CONSUMED_ENERGY, ENERGY_TOTAL_CONSUMED_ENERGY,
                        StateMappers::energy));

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
