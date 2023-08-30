/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import static org.openhab.binding.mercedesme.internal.Constants.*;

import java.util.Map;
import java.util.Optional;

import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.VehicleConfiguration;
import org.openhab.binding.mercedesme.internal.proto.VehicleEvents.VEPUpdate;
import org.openhab.binding.mercedesme.internal.proto.VehicleEvents.VehicleAttributeStatus;
import org.openhab.binding.mercedesme.internal.utils.ChannelStateMap;
import org.openhab.binding.mercedesme.internal.utils.Mapper;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class VehicleHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(VehicleHandler.class);
    private Optional<AccountHandler> accountHandler = Optional.empty();
    private Optional<QuantityType<?>> rangeElectric = Optional.empty();
    private Optional<VehicleConfiguration> config = Optional.empty();
    private Optional<QuantityType<?>> rangeFuel = Optional.empty();

    public VehicleHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = Optional.of(getConfigAs(VehicleConfiguration.class));
        Bridge bridge = getBridge();
        if (bridge != null) {
            updateStatus(ThingStatus.UNKNOWN);
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                accountHandler = Optional.of((AccountHandler) handler);
                accountHandler.get().registerVin(config.get().vin, this);
            } else {
                throw new IllegalStateException("BridgeHandler is null");
            }
        } else {
            String textKey = Constants.STATUS_TEXT_PREFIX + "vehicle" + Constants.STATUS_BRIDGE_MISSING;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, textKey);
        }
    }

    @Override
    public void dispose() {
        accountHandler.get().unregisterVin(config.get().vin);
    }

    public void distributeContent(VEPUpdate data) {
        Map<String, VehicleAttributeStatus> atts = data.getAttributesMap();
        atts.forEach((key, value) -> {
            ChannelStateMap csm = Mapper.getChannelStateMap(key, value);
            if (csm.isValid()) {
                updateChannel(csm);

                /**
                 * handle some specific channels
                 */
                // store ChannelMap for range radius calculation
                String channel = csm.getChannel();
                if ("range-electric".equals(channel)) {
                    rangeElectric = Optional.of((QuantityType<?>) csm.getState());
                } else if ("range-fuel".equals(channel)) {
                    rangeFuel = Optional.of((QuantityType<?>) csm.getState());
                } else if ("soc".equals(channel)) {
                    if (config.get().batteryCapacity > 0) {
                        float socValue = ((QuantityType<?>) csm.getState()).floatValue();
                        float batteryCapacity = config.get().batteryCapacity;
                        float chargedValue = Math.round(socValue * 1000 * batteryCapacity / 1000) / (float) 100;
                        ChannelStateMap charged = new ChannelStateMap("charged", GROUP_RANGE,
                                QuantityType.valueOf(chargedValue, Units.KILOWATT_HOUR));
                        updateChannel(charged);
                        float unchargedValue = Math.round((100 - socValue) * 1000 * batteryCapacity / 1000)
                                / (float) 100;
                        ChannelStateMap uncharged = new ChannelStateMap("uncharged", GROUP_RANGE,
                                QuantityType.valueOf(unchargedValue, Units.KILOWATT_HOUR));
                        updateChannel(uncharged);
                    } else {
                        logger.debug("No battery capacity given");
                    }
                } else if ("fuel-level".equals(channel)) {
                    if (config.get().fuelCapacity > 0) {
                        float fuelLevelValue = ((QuantityType<?>) csm.getState()).floatValue();
                        float fuelCapacity = config.get().fuelCapacity;
                        float litersInTank = Math.round(fuelLevelValue * 1000 * fuelCapacity / 1000) / (float) 100;
                        ChannelStateMap tankFilled = new ChannelStateMap("tank-remain", GROUP_RANGE,
                                QuantityType.valueOf(litersInTank, Units.LITRE));
                        updateChannel(tankFilled);
                        float litersFree = Math.round((100 - fuelLevelValue) * 1000 * fuelCapacity / 1000)
                                / (float) 100;
                        ChannelStateMap tankOpen = new ChannelStateMap("tank-open", GROUP_RANGE,
                                QuantityType.valueOf(litersFree, Units.LITRE));
                        updateChannel(tankOpen);
                    } else {
                        logger.debug("No fuel capacity given");
                    }
                }
            } else {
                logger.warn("Unable to deliver state for {}", key);
            }
        });
    }

    private void updateRadius() {
        if (rangeElectric.isPresent()) {
            // update electric radius
            ChannelStateMap radiusElectric = new ChannelStateMap("radius-electric", GROUP_RANGE,
                    guessRangeRadius(rangeElectric.get()));
            updateChannel(radiusElectric);
            if (rangeFuel.isPresent()) {
                // update fuel & hybrid radius
                ChannelStateMap radiusFuel = new ChannelStateMap("radius-fuel", GROUP_RANGE,
                        guessRangeRadius(rangeFuel.get()));
                updateChannel(radiusFuel);
                int hybridKm = rangeElectric.get().intValue() + rangeFuel.get().intValue();
                QuantityType<Length> hybridRangeState = QuantityType.valueOf(hybridKm, KILOMETRE_UNIT);
                ChannelStateMap rangeHybrid = new ChannelStateMap("range-hybrid", GROUP_RANGE, hybridRangeState);
                updateChannel(rangeHybrid);
                ChannelStateMap radiusHybrid = new ChannelStateMap("radius-hybrid", GROUP_RANGE,
                        guessRangeRadius(hybridRangeState));
                updateChannel(radiusHybrid);
            }
        } else if (rangeFuel.isPresent()) {
            // update fuel & hybrid radius
            ChannelStateMap radiusFuel = new ChannelStateMap("radius-fuel", GROUP_RANGE,
                    guessRangeRadius(rangeFuel.get()));
            updateChannel(radiusFuel);
        }
    }

    /**
     * Easy function but there's some measures behind:
     * Guessing the range of the Vehicle on Map. If you can drive x kilometers with your Vehicle it's not feasible to
     * project this x km Radius on Map. The roads to be taken are causing some overhead because they are not a straight
     * line from Location A to B.
     * I've taken some measurements to calculate the overhead factor based on Google Maps
     * Berlin - Dresden: Road Distance: 193 air-line Distance 167 = Factor 87%
     * Kassel - Frankfurt: Road Distance: 199 air-line Distance 143 = Factor 72%
     * After measuring more distances you'll find out that the outcome is between 70% and 90%. So
     *
     * This depends also on the roads of a concrete route but this is only a guess without any Route Navigation behind
     *
     * @param range
     * @return mapping from air-line distance to "real road" distance
     */
    public static State guessRangeRadius(QuantityType<?> s) {
        double radius = s.intValue() * 0.8;
        return QuantityType.valueOf(Math.round(radius), KILOMETRE_UNIT);
    }

    protected void updateChannel(ChannelStateMap csm) {
        updateState(new ChannelUID(thing.getUID(), csm.getGroup(), csm.getChannel()), csm.getState());
    }

    @Override
    public void updateStatus(ThingStatus ts, ThingStatusDetail tsd, @Nullable String details) {
        super.updateStatus(ts, tsd, details);
    }
}
