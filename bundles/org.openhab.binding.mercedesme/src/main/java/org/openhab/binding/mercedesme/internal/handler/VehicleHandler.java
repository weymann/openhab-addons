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
import java.util.UUID;

import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.VehicleConfiguration;
import org.openhab.binding.mercedesme.internal.proto.Client.ClientMessage;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.BatteryMaxSocConfigure;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.CommandRequest;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.DoorsLock;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.DoorsUnlock;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SigPosStart;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SigPosStart.HornType;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SigPosStart.LightType;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SigPosStart.SigposType;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SunroofClose;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SunroofLift;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.SunroofOpen;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.TemperatureConfigure;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.TemperatureConfigure.TemperaturePoint;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.TemperatureConfigure.TemperaturePoint.Zone;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.WindowsClose;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.WindowsOpen;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.WindowsVentilate;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.ZEVPreconditioningStart;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.ZEVPreconditioningStop;
import org.openhab.binding.mercedesme.internal.proto.VehicleCommands.ZEVPreconditioningType;
import org.openhab.binding.mercedesme.internal.proto.VehicleEvents.VEPUpdate;
import org.openhab.binding.mercedesme.internal.proto.VehicleEvents.VehicleAttributeStatus;
import org.openhab.binding.mercedesme.internal.utils.ChannelStateMap;
import org.openhab.binding.mercedesme.internal.utils.Mapper;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PointType;
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
import org.openhab.core.types.RefreshType;
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
        logger.info("Received command {} for {}", command, channelUID);
        if (command instanceof RefreshType) {
            // todo
        } else if (Constants.GROUP_HVAC.equals(channelUID.getGroupId())) {
            String pin = accountHandler.get().config.get().pin;
            if ("air-condition-temp".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandZevPreconditionConfigure");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Air Conditioning Temperature Setting supported? {}", supported);
                } else {
                    logger.info("Received Air Condition Temperature change {}", command.getClass());
                    logger.info("Received DecimalType {}", ((QuantityType) command).doubleValue());
                    TemperatureConfigure tc = TemperatureConfigure.newBuilder()
                            .addTemperaturePoints(TemperaturePoint.newBuilder().setZone(Zone.FRONT_CENTER)
                                    .setTemperatureInCelsius(((QuantityType) command).doubleValue()).build())
                            .build();
                    CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                            .setRequestId(UUID.randomUUID().toString()).setTemperatureConfigure(tc).build();
                    ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                    accountHandler.get().sendCommand(cm);
                }
            } else if ("air-condition".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandZevPreconditioningStart");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Air Conditioning supported? {}", supported);
                } else {
                    if (OnOffType.ON.equals(command)) {
                        ZEVPreconditioningStart precondStart = ZEVPreconditioningStart.newBuilder()
                                .setType(ZEVPreconditioningType.NOW).build();
                        CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                .setRequestId(UUID.randomUUID().toString()).setZevPreconditioningStart(precondStart)
                                .build();
                        ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                        accountHandler.get().sendCommand(cm);
                    } else {
                        ZEVPreconditioningStop precondStop = ZEVPreconditioningStop.newBuilder()
                                .setType(ZEVPreconditioningType.NOW).build();
                        CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                .setRequestId(UUID.randomUUID().toString()).setZevPreconditioningStop(precondStop)
                                .build();
                        ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                        accountHandler.get().sendCommand(cm);
                    }
                }
            } else if ("position".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandSigposStart");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Signal Position supported? {}", supported);
                } else {
                    SigPosStart sps;
                    CommandRequest cr;
                    ClientMessage cm;
                    switch (((DecimalType) command).intValue()) {
                        case 0: // light
                            sps = SigPosStart.newBuilder().setSigposType(SigposType.LIGHT_ONLY)
                                    .setLightType(LightType.DIPPED_HEAD_LIGHT).setSigposDuration(10).build();
                            cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                    .setRequestId(UUID.randomUUID().toString()).setSigposStart(sps).build();
                            cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                            accountHandler.get().sendCommand(cm);
                            break;
                        case 1: // horn
                            sps = SigPosStart.newBuilder().setSigposType(SigposType.HORN_ONLY).setHornRepeat(3)
                                    .setHornType(HornType.HORN_LOW_VOLUME).build();
                            cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                    .setRequestId(UUID.randomUUID().toString()).setSigposStart(sps).build();
                            cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                            accountHandler.get().sendCommand(cm);
                            break;
                        default:
                            logger.info("No Positioning known for {}", command);
                    }
                }
            } else if ("max-soc".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandBatteryMaxSocConfigure");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Max SoC configuration supported? {}", supported);
                } else {
                    BatteryMaxSocConfigure batteryMax = BatteryMaxSocConfigure.newBuilder()
                            .setMaxSoc(((QuantityType) command).intValue()).build();
                    CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                            .setRequestId(UUID.randomUUID().toString()).setBatteryMaxSoc(batteryMax).build();
                    ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                    accountHandler.get().sendCommand(cm);
                }
            } else if ("door-lock".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandDoorsLock");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Door Lock supported? {}", supported);
                } else {
                    if (OnOffType.ON.equals(command)) {
                        DoorsLock dl = DoorsLock.newBuilder().build();
                        CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                .setRequestId(UUID.randomUUID().toString()).setDoorsLock(dl).build();
                        ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                        accountHandler.get().sendCommand(cm);
                    } else {
                        if (Constants.NOT_SET.equals(pin)) {
                            logger.info("Security PIN? {}", pin);
                        } else {
                            DoorsUnlock du = DoorsUnlock.newBuilder().setPin(pin).build();
                            CommandRequest cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                    .setRequestId(UUID.randomUUID().toString()).setDoorsUnlock(du).build();
                            ClientMessage cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                            accountHandler.get().sendCommand(cm);
                        }
                    }
                }
            } else if ("control".equals(channelUID.getIdWithoutGroup())
                    && Constants.GROUP_WINDOWS.equals(channelUID.getGroupId())) {
                String supported = thing.getProperties().get("commandWindowsOpen");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Windows supported? {}", supported);
                } else {
                    CommandRequest cr;
                    ClientMessage cm;
                    switch (((DecimalType) command).intValue()) {
                        case 0:
                            WindowsClose wc = WindowsClose.newBuilder().build();
                            cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                    .setRequestId(UUID.randomUUID().toString()).setWindowsClose(wc).build();
                            cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                            accountHandler.get().sendCommand(cm);
                            break;
                        case 1:
                            if (Constants.NOT_SET.equals(pin)) {
                                logger.info("Security PIN? {}", pin);
                            } else {
                                WindowsOpen wo = WindowsOpen.newBuilder().setPin(pin).build();
                                cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                        .setRequestId(UUID.randomUUID().toString()).setWindowsOpen(wo).build();
                                cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                                accountHandler.get().sendCommand(cm);
                            }
                            break;
                        case 2:
                            if (Constants.NOT_SET.equals(pin)) {
                                logger.info("Security PIN? {}", pin);
                            } else {
                                WindowsVentilate wv = WindowsVentilate.newBuilder().setPin(pin).build();
                                cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                        .setRequestId(UUID.randomUUID().toString()).setWindowsVentilate(wv).build();
                                cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                                accountHandler.get().sendCommand(cm);
                            }
                            break;
                        default:
                            logger.info("No Windows movement known for {}", command);
                    }
                }
            } else if ("sunroof-control".equals(channelUID.getIdWithoutGroup())) {
                String supported = thing.getProperties().get("commandSunroofOpen");
                if (Boolean.FALSE.toString().equals(supported)) {
                    logger.info("Sunroof supported? {}", supported);
                } else {
                    CommandRequest cr;
                    ClientMessage cm;
                    switch (((DecimalType) command).intValue()) {
                        case 0:
                            SunroofClose sc = SunroofClose.newBuilder().build();
                            cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                    .setRequestId(UUID.randomUUID().toString()).setSunroofClose(sc).build();
                            cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                            accountHandler.get().sendCommand(cm);
                            break;
                        case 1:
                            if (Constants.NOT_SET.equals(pin)) {
                                logger.info("Security PIN? {}", pin);
                            } else {
                                SunroofOpen so = SunroofOpen.newBuilder().setPin(pin).build();
                                cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                        .setRequestId(UUID.randomUUID().toString()).setSunroofOpen(so).build();
                                cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                                accountHandler.get().sendCommand(cm);
                            }
                            break;
                        case 2:
                            if (Constants.NOT_SET.equals(pin)) {
                                logger.info("Security PIN? {}", pin);
                            } else {
                                SunroofLift sl = SunroofLift.newBuilder().setPin(pin).build();
                                cr = CommandRequest.newBuilder().setVin(config.get().vin)
                                        .setRequestId(UUID.randomUUID().toString()).setSunroofLift(sl).build();
                                cm = ClientMessage.newBuilder().setCommandRequest(cr).build();
                                accountHandler.get().sendCommand(cm);
                            }
                            break;
                        default:
                            logger.info("No Sunroof movement known for {}", command);
                    }
                }
            } else {
                logger.info("Command {} with value {} not known", channelUID, command);
            }
        }
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
        updateStatus(ThingStatus.ONLINE);
        Map<String, VehicleAttributeStatus> atts = data.getAttributesMap();
        // handle GPS
        if (atts.containsKey("positionLat") && atts.containsKey("positionLong")) {
            String gps = atts.get("positionLat").getDoubleValue() + "," + atts.get("positionLong").getDoubleValue();
            PointType pt = new PointType(gps);
            updateChannel(new ChannelStateMap("gps", Constants.GROUP_POSITION, pt));
        }
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
                // logger.trace("Unable to deliver state for {}", key);
            }
        });
        updateRadius();
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
