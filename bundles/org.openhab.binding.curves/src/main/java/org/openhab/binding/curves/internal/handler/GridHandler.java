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

import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.GridConfiguration;
import org.openhab.binding.curves.internal.interfaces.Grid;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Hems.ControllerCommand;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
import org.openhab.core.items.Item;
import org.openhab.core.items.TimeSeriesListener;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GridHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class GridHandler extends BaseEntityHandler implements Grid, TimeSeriesListener {

    private final Logger logger = LoggerFactory.getLogger(GridHandler.class);
    private TimeSeries priceSeries = new TimeSeries(Policy.REPLACE);
    private GridConfiguration config;

    public GridHandler(Thing thing) {
        super(thing);
        config = getConfigAs(GridConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(GridConfiguration.class);
        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
                    control.registerEntity(this);
                    internalUpdateStatus(ThingStatus.ONLINE, null, null);
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
        // check config
        // forecast item is mandatory
        if (!config.dynamicTariffItem.isBlank()) {
            items().getGenericItem(config.dynamicTariffItem).ifPresentOrElse(dti -> {
                logger.warn("[CURVES_GRID] added timeseries listener - wait for update");
                dti.addTimeSeriesListener(this);
                scheduler.schedule(this::initialUpdate, 0, TimeUnit.MINUTES);
            }, () -> {
                logger.warn("[CURVES_GRID] Dynamic Grid Tariff Item {} not found", config.dynamicTariffItem);
            });
        }
    }

    @Override
    public void dispose() {
        controller().deregisterEntity(this);
        super.dispose();
    }

    @Override
    public void sendGridConsumptionTimeSeries(TimeSeries consumption) {
        sendTimeSeries(new ChannelUID(thing.getUID(), CHANNEL_FROM_GRID_CAST), consumption);
    }

    @Override
    public void sendGridSupplyTimeSeries(TimeSeries supply) {
        sendTimeSeries(new ChannelUID(thing.getUID(), CHANNEL_TO_GRID_CAST), supply);
    }

    private void initialUpdate() {
        Instant start = datalake().getPredictionStart();
        Instant end = datalake().getPredictionEnd();
        logger.info("[CURVES_GRID] Request Proces from {} to {}", start, end);
        TimeSeries spotPriceSeries = items().getHistoricTimeSeries(config.dynamicTariffItem, start, end);
        TreeMap<Instant, State> spotPriceTree = curves().getNormalizedCurve(spotPriceSeries, CalculationMethod.AVERAGE,
                false);
        logger.info("[CURVES_GRID] Received prices from {} to {} with {} entries", spotPriceSeries.getBegin(),
                spotPriceSeries.getEnd(), spotPriceSeries.size());
        items().getGenericItem(config.dynamicTariffItem).ifPresentOrElse(dti -> {
            timeSeriesUpdated(dti, curves().toTimeSeries(spotPriceTree));
        }, () -> {
            logger.warn("[CURVES_GRID] Priceitem Item {} not found", config.dynamicTariffItem);
        });
    }

    @Override
    public void timeSeriesUpdated(Item item, TimeSeries timeSeries) {
        // check if this is really a change
        if (!priceSeries.getEnd().equals(timeSeries.getEnd())) {
            priceSeries = timeSeries;
            controller().handle(this, ControllerCommand.PRICE_UPDATE);
        } else {
            logger.info("[CURVES_GRID] Price series update but no change");
        }
    }

    @Override
    public TreeMap<Instant, State> getPriceMap() {
        return curves().getNormalizedCurve(priceSeries, CalculationMethod.AVERAGE, false);
    }
}
