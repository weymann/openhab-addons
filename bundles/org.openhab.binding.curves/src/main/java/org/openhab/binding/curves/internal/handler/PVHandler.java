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

import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.PVConfiguration;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Hems.ControllerCommand;
import org.openhab.binding.curves.internal.interfaces.PV;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
import org.openhab.core.items.Item;
import org.openhab.core.items.TimeSeriesListener;
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
 * The {@link PVHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PVHandler extends BaseEntityHandler implements PV, TimeSeriesListener {

    private final Logger logger = LoggerFactory.getLogger(PVHandler.class);
    private TreeMap<Instant, State> powerForecastMap = new TreeMap<>();
    private TreeMap<Instant, State> powerSeries = new TreeMap<>();
    private TreeMap<Instant, State> energySeries = new TreeMap<>();
    private TreeMap<Instant, State> energyForecastSeries = new TreeMap<>();

    protected PVConfiguration config;

    public PVHandler(Thing thing) {
        super(thing);
        config = getConfigAs(PVConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(PVConfiguration.class);

        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
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

        // check config forecast item is mandatory
        if (!config.powerForecastItem.isBlank()) {
            items().getGenericItem(config.powerForecastItem).ifPresentOrElse(pfi -> {
                logger.warn("[CURVES_PV] add timeseries listener");
                pfi.addTimeSeriesListener(this);
                internalUpdateStatus(ThingStatus.UNKNOWN, null, null);
                scheduler.schedule(this::initialUpdate, 0, TimeUnit.SECONDS);
            }, () -> {
                logger.warn("[CURVES_PV] Power Forecast item {} not found", config.powerForecastItem);
            });
        }
    }

    @Override
    public void dispose() {
        controller().deregisterEntity(this);
        super.dispose();
    }

    @Override
    public void timeSeriesUpdated(Item item, TimeSeries timeSeries) {
        logger.info("[CURVES_PV] TimeSeries update for {} from {} till {}", item.getName(), timeSeries.getBegin(),
                timeSeries.getEnd());
        if (item.getName().equals(config.powerForecastItem)) {
            TreeMap<Instant, State> updateMap = curves().getNormalizedCurve(timeSeries, CalculationMethod.AVERAGE,
                    false);
            updateMap.forEach((key, value) -> {
                powerForecastMap.put(key, value);
            });
            // logger.info("[CURVES_PV] {}", powerForecastSeries);
            internalUpdateStatus(ThingStatus.ONLINE, null, null);
            confirmationMap.put(controller().handle(this, ControllerCommand.PV_UPDATE), ControllerCommand.PV_UPDATE);
        } else {
            logger.info("[CURVES_PV] timeseries {} not expected from item {}", timeSeries, item.getName());
        }
    }

    @Override
    public TreeMap<Instant, State> getPredictionMap(Type type, Unit unit) {
        TreeMap<Instant, State> returnSeries = new TreeMap<>();
        switch (type) {
            case FORECAST:
                switch (unit) {
                    case POWER:
                        returnSeries = powerForecastMap;
                        break;
                    case ENERGY:
                        returnSeries = energyForecastSeries;
                        break;
                }
                break;
            case INVERTER:
                switch (unit) {
                    case POWER:
                        returnSeries = powerSeries;
                        break;
                    case ENERGY:
                        returnSeries = energySeries;
                        break;
                }
                break;
        }
        return returnSeries;
    }

    private void initialUpdate() {
        Instant start = datalake().getPredictionStart();
        Instant end = datalake().getPredictionEnd();
        logger.info("[CURVES_PV] Wanted:   Forecast from {} to {}", start, end);
        TimeSeries powerForecastSeries = items().getHistoricTimeSeries(config.powerForecastItem, start, end);
        // ensure pv forecast starts / ends with 0
        powerForecastSeries.add(start, QuantityType.valueOf("0 W"));
        powerForecastSeries.add(end, QuantityType.valueOf("0 W"));
        logger.info("[CURVES_PV] Wanted:   Forecast from {} to {}", start, end);

        powerForecastMap = curves().getNormalizedCurve(powerForecastSeries, CalculationMethod.AVERAGE, false);
        logger.info("[CURVES_PV] Received: Forecast from {} to {} with {} entries", powerForecastSeries.getBegin(),
                powerForecastSeries.getEnd(), powerForecastSeries.size());
        items().getGenericItem(config.powerForecastItem).ifPresentOrElse(pfi -> {
            timeSeriesUpdated(pfi, curves().toTimeSeries(powerForecastMap));
        }, () -> {
            logger.warn("[CURVES_PV] Powerforcast Item {} not found", config.powerForecastItem);

        });
    }
}
