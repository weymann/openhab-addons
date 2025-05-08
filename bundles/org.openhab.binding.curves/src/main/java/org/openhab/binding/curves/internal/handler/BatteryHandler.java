/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal.handler;

import static org.openhab.binding.curves.internal.CurvesConstants.*;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.BatteryConfiguration;
import org.openhab.binding.curves.internal.interfaces.Battery;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.StateChangeListener;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BatteryHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BatteryHandler extends BaseEntityHandler implements Battery, StateChangeListener {

    private final Logger logger = LoggerFactory.getLogger(BatteryHandler.class);
    private BatteryConfiguration config;
    private Optional<GenericItem> socItem = Optional.empty();
    private Optional<GenericItem> batteryEnergyItem = Optional.empty();

    public BatteryHandler(Thing thing) {
        super(thing);
        config = getConfigAs(BatteryConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
                    registerStateListeners();
                    control.registerEntity(this);
                } else {
                    internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "@text/solarforecast.plane.status.wrong-handler" + " [\"" + handler + "\"]");
                    return;
                }
            } else {
                internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/solarforecast.plane.status.bridge-handler-not-found");
                return;
            }
        } else {
            internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/solarforecast.plane.status.bridge-missing");
            return;
        }
    }

    private void registerStateListeners() {
        if (!config.stateOfChargeItem.isBlank()) {
            socItem = items().getGenericItem(config.stateOfChargeItem);
            socItem.ifPresentOrElse(soc -> {
                soc.addStateChangeListener(this);
            }, () -> {
                logger.info("[BAT] SoC Item not configured");
            });
        }
        if (!config.chargeItem.isBlank()) {
            items().getGenericItem(config.chargeItem).ifPresentOrElse(cpi -> {
                cpi.addStateChangeListener(this);
            }, () -> {
                logger.info("[BAT] Charge Power item not configured");
            });
        }
        if (!config.dischargeItem.isBlank()) {
            items().getGenericItem(config.dischargeItem).ifPresentOrElse(dpi -> {
                dpi.addStateChangeListener(this);
            }, () -> {
                logger.info("[BAT] Discharge Power item not configured");
            });
        }

        // now check - if batteryEnergy is delivering -1 configuration is wrong - otherwise online
        if (getCharge() >= 0) {
            super.internalUpdateStatus(ThingStatus.ONLINE, null, null);
        } else {
            super.internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Battery energy measurement is mandatory. Either configure SoC and capacity or battery energy item");
        }
    }

    @Override
    public void dispose() {
        controller().deregisterEntity(this);
        super.dispose();
    }

    @Override
    public double getStateOfCharge() {
        if (socItem.isPresent()) {
            return socItem.get().getState().as(QuantityType.class).doubleValue();
        }
        return -1;
    }

    @Override
    public double getCapacity() {
        return config.batteryCapacity;
    }

    @Override
    public double getCharge() {
        if (batteryEnergyItem.isPresent()) {
            return batteryEnergyItem.get().getState().as(QuantityType.class).doubleValue();
        } else if (socItem.isPresent()) {
            return getCapacity() * getStateOfCharge() / 100;
        } else {
            logger.warn("[BAT] neither soc + capacity NOR battery energy configured. Battery cannot be measured");
            return -1;
        }
    }

    @Override
    public void stateChanged(Item item, State oldState, State newState) {
        // TODO Auto-generated method stub
    }

    @Override
    public void stateUpdated(Item item, State state) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCargeTimeSeries(TimeSeries charge) {
        sendTimeSeries(new ChannelUID(thing.getUID(), CHANNEL_CHARGE_CAST), charge);
    }

    @Override
    public void setDischargeTimeSeries(TimeSeries discharge) {
        sendTimeSeries(new ChannelUID(thing.getUID(), CHANNEL_DISCHARGE_CAST), discharge);
    }

    @Override
    public long getMaximumChargePower() {
        return config.maxChargePower;
    }

    @Override
    public long getMaximumDischargePower() {
        return config.maxDischargePower;
    }
}
