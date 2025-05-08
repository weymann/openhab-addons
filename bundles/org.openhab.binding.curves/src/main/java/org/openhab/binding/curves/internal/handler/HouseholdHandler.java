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

import static org.openhab.binding.curves.internal.CurvesConstants.CHANNEL_HOUSEHOLD_CAST;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.HouseholdConfiguration;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Hems.ControllerCommand;
import org.openhab.binding.curves.internal.interfaces.Household;
import org.openhab.binding.curves.internal.utils.CurveUtils.CalculationMethod;
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
import org.openhab.core.types.TimeSeries.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HouseholdHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class HouseholdHandler extends BaseEntityHandler implements Household {

    private final Logger logger = LoggerFactory.getLogger(HouseholdHandler.class);
    private TreeMap<Instant, State> householdMap = new TreeMap<>();
    private Optional<ScheduledFuture<?>> refreshJob = Optional.empty();
    private HouseholdConfiguration config;
    private int day = -1;

    public HouseholdHandler(Thing thing) {
        super(thing);
        config = getConfigAs(HouseholdConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(HouseholdConfiguration.class);
        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
                    control.registerEntity(this);
                    internalUpdateStatus(ThingStatus.UNKNOWN, null, null);
                    refreshJob = Optional.of(scheduler.schedule(this::checkForUpdate, 0, TimeUnit.MINUTES));
                } else {
                    internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "@text/curves.status.wrong-handler" + " [\"" + handler + "\"]");
                    return;
                }
            } else {
                internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/curves.status.bridge-handler-not-found");
                return;
            }
        } else {
            internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/curves.status.bridge-missing");
            return;
        }
    }

    @Override
    public void dispose() {
        refreshJob.ifPresent(job -> job.cancel(true));
        controller().deregisterEntity(this);
        super.dispose();
    }

    @Override
    public TreeMap<Instant, State> getPredictionMap() {
        return householdMap;
    }

    private void updatePrediction() {
        /**
         * Get history from same day last week
         * todo check improvement e.g. average of last 4 weeks, same day
         */
        Instant historyStart = times().startOfDay(-7);
        Instant historyEnd = datalake().getPredictionStart();
        TimeSeries historySeries = items().getHistoricTimeSeries(config.householdPowerItem, historyStart, historyEnd);
        if (historySeries.size() == 0) {
            logger.info("[CURVES_HOUSEHOLD] No history found - deliver flatline");
            historySeries.add(historyStart, QuantityType.valueOf("500 W"));
            historySeries.add(historyEnd, QuantityType.valueOf("500 W"));
        } else {
            logger.info("[CURVES_HOUSEHOLD] Found {} entries in history starting at {}", historySeries.size(),
                    historySeries.getBegin());
            long minutesToStart = Duration.between(historyStart, historySeries.getBegin()).toMinutes();
            if (minutesToStart > 0) {
                logger.info(
                        "[CURVES_HOUSEHOLD] First item found {} minutes after start - deliver flatline for missing values",
                        minutesToStart);
                historySeries.add(historyStart, QuantityType.valueOf("500 W"));
            }
        }

        /**
         * Convert history timeseries to household prediction
         */
        final TimeSeries householdSeries = new TimeSeries(Policy.REPLACE);
        historySeries.getStates().forEach(state -> {
            householdSeries.add(state.timestamp().plus(7, ChronoUnit.DAYS), state.state());
        });

        householdMap = curves().getNormalizedCurve(householdSeries, CalculationMethod.AVERAGE, false);
        logger.info("[CURVES_HOUSEHOLD] household prediction update from {} to {} with {} elements",
                householdMap.firstKey(), householdMap.lastKey(), householdMap.size());
        // logger.info("[CURVES_HOUSEHOLD] {}", householdMap);
        internalUpdateStatus(ThingStatus.ONLINE, null, null);
        confirmationMap.put(controller().handle(this, ControllerCommand.HOUSEHOLD_UPDATE),
                ControllerCommand.HOUSEHOLD_UPDATE);
        TimeSeries futureHouseholdSeries = curves().toTimeSeries(householdMap);
        super.sendTimeSeries(new ChannelUID(thing.getUID(), CHANNEL_HOUSEHOLD_CAST), futureHouseholdSeries);

        day = times().getDay();
    }

    private void checkForUpdate() {
        if (day != times().getDay()) {
            updatePrediction();
            day = times().getDay();
        }
    }
}
