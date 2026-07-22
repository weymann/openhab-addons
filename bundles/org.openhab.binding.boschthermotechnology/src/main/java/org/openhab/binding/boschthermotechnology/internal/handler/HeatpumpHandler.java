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
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CHARGE_DURATION_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CHARGE_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_OPERATION_MODE_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_REDUCE_TEMP_ON_ALARM_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_SINGLE_CHARGE_SETPOINT_TEMPLATE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEATING_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_CH_STATUS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_ID_DEFAULT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_NUMBER_OF_STARTS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_RETURN_TEMPERATURE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.MANUAL_ROOM_SETPOINT_TEMPLATE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;
import org.openhab.binding.boschthermotechnology.internal.config.SubThingConfiguration;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ChannelResourceBinding;
import org.openhab.binding.boschthermotechnology.internal.handler.support.CommandWriters;
import org.openhab.binding.boschthermotechnology.internal.handler.support.ResourcePollingSupport;
import org.openhab.binding.boschthermotechnology.internal.handler.support.StateMappers;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * The {@link HeatpumpHandler} represents the heating circuits, DHW circuits, and heat source(s)
 * of one gateway - the functional core of what the old, monolithic {@code gateway} Thing used to
 * be before ADR-005/ADR-006.
 *
 * <p>
 * Unlike every other child thing-type, its channel groups are not static: {@code initialize()}
 * reads {@code resource/heatingCircuits} and {@code resource/dhwCircuits} to discover how many
 * circuits this gateway actually has, and builds one {@code heating-circuit-{circuitId}}/
 * {@code dhw-circuit-{circuitId}} channel group per discovered circuit id (e.g.
 * {@code heating-circuit-hc1}), generalizing the single hardcoded {@code hc1}/{@code dhw1} channels
 * this binding exposed before ADR-006. Group names are keyed by the circuit's own id, not its
 * position in the API's response list - see the {@code $QA} comment in
 * {@code discoverCircuitsAndStartPolling} for why. The single {@code heat-source-1} group is always
 * added using the flat, non-circuit-id heat-source paths - see
 * {@link org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths}
 * for why heat sources are not templated the same way yet.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class HeatpumpHandler extends BaseThingHandler {

    private static final String BINDING_ID = "boschthermotechnology";
    private static final String GROUP_HEATING_CIRCUIT_PREFIX = "heating-circuit-";
    private static final String GROUP_DHW_CIRCUIT_PREFIX = "dhw-circuit-";
    private static final String GROUP_HEAT_SOURCE_PREFIX = "heat-source-";

    /**
     * Bidirectional mapping between the {@code dhw-operation-mode} channel's Number codes (see the
     * {@code option} values in {@code thing-types.xml}) and the String values the PointT API
     * actually reports/expects - unchanged from the pre-ADR-006 {@code GatewayHandler}.
     */
    private static final Map<Integer, String> DHW_OPERATION_MODE_TO_API_VALUE = Map.of(0, "Off", 1, "Eco+", 2, "Eco", 3,
            "Comfort", 4, "Auto");
    private static final Map<String, Integer> DHW_OPERATION_MODE_TO_CODE = DHW_OPERATION_MODE_TO_API_VALUE.entrySet()
            .stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private final Logger logger = LoggerFactory.getLogger(HeatpumpHandler.class);

    private @Nullable ResourcePollingSupport pollingSupport;
    private @Nullable Future<?> discoveryFuture;

    public HeatpumpHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        SubThingConfiguration config = getConfigAs(SubThingConfiguration.class);
        if (config.gatewayId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "gatewayId must not be empty");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
        // $QA: submit() instead of execute() so a rapid initialize()/dispose() (e.g. the user
        // reconfigures the thing seconds after adding it) can actually cancel this in-flight
        // discovery call from dispose() - see the cancellation there.
        discoveryFuture = scheduler.submit(() -> discoverCircuitsAndStartPolling(config));
    }

    /**
     * Reads the circuit lists once at startup to build this thing's dynamic channel groups, then
     * starts regular polling. Runs on the handler's own scheduler (not the openHAB event thread)
     * since it makes blocking HTTP calls, exactly like the regular poll cycle does.
     */
    private void discoverCircuitsAndStartPolling(SubThingConfiguration config) {
        GatewayHandler gatewayHandler = getGatewayHandler();
        if (gatewayHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        try {
            String accessToken = gatewayHandler.getValidAccessToken();
            PointTApiClient apiClient = gatewayHandler.getApiClient();
            List<String> heatingCircuitIds = apiClient.listResourceIds(accessToken, config.gatewayId,
                    HEATING_CIRCUITS_LIST);
            List<String> dhwCircuitIds = apiClient.listResourceIds(accessToken, config.gatewayId, DHW_CIRCUITS_LIST);

            // $QA: both lists empty is treated as a discovery failure, not "zero circuits" - bail
            // out *before* touching this thing's channels. Rebuilding with an empty/near-empty
            // channel list here would silently unlink every Item the user already has on a working
            // thing if a transient API hiccup returned no circuits on a later re-initialize().
            if (heatingCircuitIds.isEmpty() && dhwCircuitIds.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "No heating or DHW circuits were reported for gateway " + config.gatewayId);
                return;
            }

            List<Channel> channels = new ArrayList<>();
            List<ChannelResourceBinding> bindings = new ArrayList<>();

            // $QA: group id is derived from the circuit's own id (e.g. "heating-circuit-hc1"), not
            // its position in the list. The PointT API gives no ordering guarantee between calls -
            // indexing by position would silently remap an existing Item link onto a different
            // physical circuit if the API ever returned the list in a different order (e.g. after a
            // gateway firmware update or an openHAB restart).
            for (String circuitId : heatingCircuitIds) {
                addHeatingCircuit(channels, bindings, GROUP_HEATING_CIRCUIT_PREFIX + circuitId, circuitId);
            }

            for (String circuitId : dhwCircuitIds) {
                addDhwCircuit(channels, bindings, GROUP_DHW_CIRCUIT_PREFIX + circuitId, circuitId);
            }

            addHeatSource(channels, bindings, GROUP_HEAT_SOURCE_PREFIX + HEAT_SOURCE_ID_DEFAULT);

            updateThing(editThing().withChannels(channels).build());

            ResourcePollingSupport support = new ResourcePollingSupport(this::updateState, this::updateStatus,
                    gatewayHandler, config.gatewayId, bindings);
            pollingSupport = support;
            support.start(scheduler, config.refreshInterval);
        } catch (PointTAuthException e) {
            gatewayHandler.invalidateAccessToken();
            logger.debug("Access token rejected while discovering circuits: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (PointTApiException e) {
            logger.warn("Could not discover heating/DHW circuits for gateway {}: {}", config.gatewayId, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void addHeatingCircuit(List<Channel> channels, List<ChannelResourceBinding> bindings, String groupId,
            String circuitId) {
        addChannel(channels, groupId, CHANNEL_HC_MANUAL_ROOM_SETPOINT, "Number:Temperature", "manual-room-setpoint");
        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_HC_MANUAL_ROOM_SETPOINT),
                MANUAL_ROOM_SETPOINT_TEMPLATE.formatted(circuitId), StateMappers::temperature, CommandWriters::number));
    }

    private void addDhwCircuit(List<Channel> channels, List<ChannelResourceBinding> bindings, String groupId,
            String circuitId) {
        addChannel(channels, groupId, CHANNEL_DHW_CHARGE_DURATION, "Number:Time", "dhw-charge-duration");
        addChannel(channels, groupId, CHANNEL_DHW_SINGLE_CHARGE_SETPOINT, "Number:Temperature",
                "dhw-single-charge-setpoint");
        addChannel(channels, groupId, CHANNEL_DHW_OPERATION_MODE, "Number", "dhw-operation-mode");
        addChannel(channels, groupId, CHANNEL_DHW_CHARGE, "Switch", "dhw-charge");
        addChannel(channels, groupId, CHANNEL_DHW_REDUCE_TEMP_ON_ALARM, "Switch", "dhw-reduce-temp-on-alarm");

        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_DHW_CHARGE_DURATION),
                DHW_CHARGE_DURATION_TEMPLATE.formatted(circuitId), StateMappers::minutes, CommandWriters::number));
        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_DHW_SINGLE_CHARGE_SETPOINT),
                DHW_SINGLE_CHARGE_SETPOINT_TEMPLATE.formatted(circuitId), StateMappers::temperature,
                CommandWriters::number));
        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_DHW_OPERATION_MODE),
                DHW_OPERATION_MODE_TEMPLATE.formatted(circuitId), this::toDhwOperationModeState,
                this::toDhwOperationModeJson));
        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_DHW_CHARGE),
                DHW_CHARGE_TEMPLATE.formatted(circuitId), StateMappers::onOff, CommandWriters::onOff));
        bindings.add(ChannelResourceBinding.readWrite(groupChannelId(groupId, CHANNEL_DHW_REDUCE_TEMP_ON_ALARM),
                DHW_REDUCE_TEMP_ON_ALARM_TEMPLATE.formatted(circuitId), StateMappers::onOff, CommandWriters::onOff));
    }

    private void addHeatSource(List<Channel> channels, List<ChannelResourceBinding> bindings, String groupId) {
        addChannel(channels, groupId, CHANNEL_HEAT_SOURCE_CH_STATUS, "String", "heat-source-ch-status");
        addChannel(channels, groupId, CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE, "Number:Temperature",
                "heat-source-actual-supply-temperature");
        addChannel(channels, groupId, CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE, "Number:Temperature",
                "heat-source-return-temperature");
        addChannel(channels, groupId, CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS, "Number", "heat-source-number-of-starts");
        addChannel(channels, groupId, CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM, "Number:Time",
                "heat-source-working-time-total-system");

        bindings.add(ChannelResourceBinding.readOnly(groupChannelId(groupId, CHANNEL_HEAT_SOURCE_CH_STATUS),
                HEAT_SOURCE_CH_STATUS, StateMappers::rawString));
        bindings.add(
                ChannelResourceBinding.readOnly(groupChannelId(groupId, CHANNEL_HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE),
                        HEAT_SOURCE_ACTUAL_SUPPLY_TEMPERATURE, StateMappers::temperature));
        bindings.add(ChannelResourceBinding.readOnly(groupChannelId(groupId, CHANNEL_HEAT_SOURCE_RETURN_TEMPERATURE),
                HEAT_SOURCE_RETURN_TEMPERATURE, StateMappers::temperature));
        bindings.add(ChannelResourceBinding.readOnly(groupChannelId(groupId, CHANNEL_HEAT_SOURCE_NUMBER_OF_STARTS),
                HEAT_SOURCE_NUMBER_OF_STARTS, StateMappers::count));
        bindings.add(
                ChannelResourceBinding.readOnly(groupChannelId(groupId, CHANNEL_HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM),
                        HEAT_SOURCE_WORKING_TIME_TOTAL_SYSTEM, StateMappers::hours));
    }

    private void addChannel(List<Channel> channels, String groupId, String channelId, String acceptedItemType,
            String channelTypeId) {
        ChannelUID channelUID = new ChannelUID(thing.getUID(), groupId, channelId);
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelTypeId);
        channels.add(ChannelBuilder.create(channelUID, acceptedItemType).withType(channelTypeUID).build());
    }

    /** Matches the format {@link ChannelUID#getId()} returns for a channel that belongs to a group. */
    private String groupChannelId(String groupId, String channelId) {
        return groupId + "#" + channelId;
    }

    /**
     * Maps the PointT API's DHW operation mode String value onto the {@code dhw-operation-mode}
     * channel's Number code. An unrecognized value is logged and reported as
     * {@link UnDefType#UNDEF} rather than failing the whole poll.
     */
    // Package-private (not private) so HeatpumpHandlerTest can exercise this mapping directly with
    // a @ParameterizedTest, per rules/testing-rules.md, without going through the full HTTP poll
    // cycle just to test a pure conversion.
    State toDhwOperationModeState(JsonElement value) {
        Integer code = DHW_OPERATION_MODE_TO_CODE.get(value.getAsString());
        if (code == null) {
            logger.debug("Unknown DHW operation mode value '{}' reported by gateway", value.getAsString());
            return UnDefType.UNDEF;
        }
        return new DecimalType(code);
    }

    JsonElement toDhwOperationModeJson(Command command) {
        if (!(command instanceof DecimalType decimal)) {
            throw new IllegalArgumentException(
                    "Unsupported command type " + command.getClass().getSimpleName() + " for dhw-operation-mode");
        }
        String apiValue = DHW_OPERATION_MODE_TO_API_VALUE.get(decimal.intValue());
        if (apiValue == null) {
            throw new IllegalArgumentException("Unknown DHW operation mode code " + decimal.intValue());
        }
        return new JsonPrimitive(apiValue);
    }

    @Override
    public void dispose() {
        // $QA: cancel a still-in-flight discovery task first. Without this, a rapid add/remove of
        // this thing could let discoverCircuitsAndStartPolling() finish *after* dispose() and call
        // updateThing()/updateStatus() on an already-disposed handler.
        Future<?> discovery = discoveryFuture;
        if (discovery != null) {
            discovery.cancel(true);
        }
        discoveryFuture = null;

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
