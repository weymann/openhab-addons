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
package org.openhab.binding.curves.internal.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CurveUtils} provides helper functions for curve and time series transformations.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class CurveUtils {
    private final Logger logger = LoggerFactory.getLogger(CurveUtils.class);
    private final long TIMEFRAME_DURATION_SEC;

    private TimeUtils timeUtils;

    /**
     * Enumeration to define the calculation methods for curve normalization.
     * - AVERAGE:
     */
    public enum CalculationMethod {
        AVERAGE,
        ACCUMULATE,
        INTERPOLATE,
        NONE
    }

    public CurveUtils(Duration granularity, TimeUtils timeUtils) {
        this.timeUtils = timeUtils;
        TIMEFRAME_DURATION_SEC = granularity.toSeconds();
    }

    /**
     * Transforms a curve represented by TreeMap into another TreeMap with the desired granularity.
     *
     * @param input
     * @return
     */
    @SuppressWarnings({ "null", "unchecked" })
    public TreeMap<Instant, State> getNormalizedCurve(TimeSeries input, CalculationMethod method, boolean debug) {
        Unit<?> calculationUnit = null;
        Optional<TimeSeries.Entry> firstEntry = input.getStates().findFirst();
        if (firstEntry.isPresent()) {
            State anyState = firstEntry.get().state();
            if (anyState instanceof QuantityType<?> qt) {
                // if the state is a QuantityType, we can get the unit
                calculationUnit = qt.getUnit();
            } else {
                logger.warn("[CURVES_UTIL] Time series contains no QuantityType - cannot determine unit");
                return new TreeMap<>();
            }
        } else {
            logger.warn("[CURVES_UTIL] Time series is empty - cannot determine unit");
            return new TreeMap<>();
        }

        TreeMap<Instant, State> normalizedMap = new TreeMap<>();
        TreeMap<Instant, State> sourceMap = toTreeMap(input);
        Instant normalizedIterationTime = timeUtils.timeFrame(input.getBegin(), 0);
        Instant normalizedEndTime = timeUtils.timeFrame(input.getEnd(), 0);
        while (!normalizedIterationTime.isAfter(normalizedEndTime)) {
            // get state for current timeframe
            State state = getState(sourceMap, calculationUnit, method, normalizedIterationTime);
            if (debug) {
                logger.info("[CURVES_UTIL] Normalized iteration at {} with state {}", normalizedIterationTime, state);
                System.out.println(
                        "[CURVES_UTIL] Normalized iteration at " + normalizedIterationTime + " with state " + state);
            }
            System.out.println("---");
            normalizedMap.put(normalizedIterationTime, state);
            // step to next timeframe
            normalizedIterationTime = normalizedIterationTime.plus(TIMEFRAME_DURATION_SEC, ChronoUnit.SECONDS);
        }
        return normalizedMap;
    }

    public State getState(TreeMap<Instant, State> source, Unit<?> unit, CalculationMethod method, Instant start) {
        // System.out.println("[CURVES_UTIL] Get state for " + start + " with method " + method.name());
        double val = 0;
        Entry<Instant, State> iterationEntry = source.floorEntry(start);
        Entry<Instant, State> endEntry = source.ceilingEntry(start.plusSeconds(TIMEFRAME_DURATION_SEC));
        if (iterationEntry == null) {
            logger.warn("[CURVES_UTIL] No start entry found for {}", start);
            iterationEntry = source.ceilingEntry(start);
        }
        if (endEntry == null) {
            logger.warn("[CURVES_UTIL] No end entry found for {}", start.plusSeconds(TIMEFRAME_DURATION_SEC));
            endEntry = source.floorEntry(start.plusSeconds(TIMEFRAME_DURATION_SEC));
        }
        if (CalculationMethod.AVERAGE.equals(method)) {
            // if average is requested, we need to calculate the value for the whole timeframe
            while (endEntry.getKey().isAfter(iterationEntry.getKey())) {
                // calculate value for timeframe
                double calculationValue = ((QuantityType<?>) iterationEntry.getValue()).doubleValue();
                Instant calculationStart = !start.isBefore(iterationEntry.getKey()) ? start : iterationEntry.getKey();
                iterationEntry = source.higherEntry(iterationEntry.getKey());
                Instant calculationEnd = !start.plusSeconds(TIMEFRAME_DURATION_SEC).isAfter(iterationEntry.getKey())
                        ? start.plusSeconds(TIMEFRAME_DURATION_SEC)
                        : iterationEntry.getKey();
                System.out.println("[CURVES_UTIL] Start calculation from " + calculationStart + " to " + calculationEnd
                        + " with value " + calculationValue);
                val += calculationValue * Duration.between(calculationStart, calculationEnd).toSeconds();
            }
            val = val / TIMEFRAME_DURATION_SEC;
        } else if (CalculationMethod.INTERPOLATE.equals(method)) {
            // interpolate value from start to end
            double firstPart = ((QuantityType<?>) iterationEntry.getValue()).doubleValue()
                    * Duration.between(start, endEntry.getKey()).toSeconds();
            double secondPart = ((QuantityType<?>) endEntry.getValue()).doubleValue()
                    * Duration.between(iterationEntry.getKey(), start).toSeconds();
            System.out.println("[CURVES_UTIL] First " + firstPart + " Second " + secondPart);
            val = (firstPart + secondPart) / Duration.between(iterationEntry.getKey(), endEntry.getKey()).toSeconds();
        } else if (CalculationMethod.NONE.equals(method)) {
            val = ((QuantityType<?>) iterationEntry.getValue()).doubleValue();
        }
        if (Double.isNaN(val)) {
            val = 0;
        }
        return QuantityType.valueOf(val, unit);
    }

    /**
     * Convert a TreeMap of Instant and State to a TimeSeries.
     *
     * @param input TreeMap of Instant and State to convert
     * @return TimeSeries containing the data from the input TreeMap
     */
    public TimeSeries toTimeSeries(TreeMap<Instant, State> input) {
        final TimeSeries returnSeries = new TimeSeries(Policy.REPLACE);
        input.forEach((key, value) -> {
            returnSeries.add(key, value);
        });
        return returnSeries;
    }

    /**
     * Convert a TimeSeries to a TreeMap of Instant and State.
     *
     * @param input TimeSeries to convert
     * @return TreeMap of Instant and State containing the data from the input TimeSeries
     */
    public TreeMap<Instant, State> toTreeMap(TimeSeries input) {
        final TreeMap<Instant, State> seriesMap = new TreeMap<>();
        input.getStates().forEach(entry -> {
            seriesMap.put(entry.timestamp(), entry.state());
        });
        return seriesMap;
    }

    /**
     * Transfer time series into the desired granularity
     *
     * @param input
     * @return
     */
    @SuppressWarnings({ "null", "unchecked" })
    @Deprecated
    private TreeMap<Instant, State> getNormalizedCurve(TimeSeries input, CalculationMethod method) {
        boolean debug = true;
        if (debug) {
            logger.info("[CURVES_UTIL] Normalized curve fromseries from {} to {} with {} entries with method {}",
                    input.getBegin(), input.getEnd(), input.size(), method.name());
        }
        TreeMap<Instant, State> normalizedMap = new TreeMap<>();
        TreeMap<Instant, State> sourceMap = toTreeMap(input);
        // Instant timeFrameStartTime = timeUtils.getStartPoint(Instant.now(timeUtils.getClock()));
        Instant timeFrameStartTime = timeUtils.timeFrame(input.getBegin(), 0);
        Instant timeFrameEndTime = timeFrameStartTime.plus(TIMEFRAME_DURATION_SEC, ChronoUnit.SECONDS);
        if (debug) {
            logger.info("[CURVES_UTIL] Starting curve investigation at {}", timeFrameStartTime);
            System.out.println("[CURVES_UTIL] Starting curve investigation at " + timeFrameStartTime);
        }
        Entry<Instant, State> currentEntry = sourceMap.floorEntry(timeFrameStartTime);
        Entry<Instant, State> nextEntry;
        if (currentEntry == null) {
            currentEntry = sourceMap.firstEntry();
            if (debug) {
                logger.info("[CURVES_UTIL] Start with first entry {} - {}", currentEntry.getKey(),
                        currentEntry.getValue());
                System.out.println("[CURVES_UTIL] Start with first entry " + currentEntry.getKey() + " - "
                        + currentEntry.getValue());
            }
        } else {
            if (debug) {
                logger.info("[CURVES_UTIL] Start with floor entry {} - {}", currentEntry.getKey(),
                        currentEntry.getValue());
                System.out.println("[CURVES_UTIL] Start with floor entry " + currentEntry.getKey() + " - "
                        + currentEntry.getValue());
            }
        }
        Unit<?> calculationUnit = null;
        nextEntry = sourceMap.higherEntry(currentEntry.getKey());

        // starting point of the new series
        double frameValue = 0;
        while (timeUtils.isBeforeOrEqual(currentEntry.getKey(), input.getEnd())) {
            if (debug) {
                logger.info("[CURVES_UTIL] Calculate timeframe {} - {} : Step {} ", timeFrameStartTime,
                        timeFrameEndTime, currentEntry.getKey());
                System.out.println("[CURVES_UTIL] Calculate timeframe " + timeFrameStartTime + " - " + timeFrameEndTime
                        + " : Step " + currentEntry.getKey());
            }
            QuantityType<?> currentValue = (QuantityType) currentEntry.getValue();
            if (calculationUnit == null) {
                calculationUnit = currentValue.getUnit();
            }

            if (nextEntry == null) {
                if (debug) {
                    logger.info("[CURVES_UTIL] no next higher entry found");
                    System.out.println("[CURVES_UTIL] no next higher entry found");
                }
                // there are no further entries - write last frameValue as entry
                if (frameValue > 0) {
                    // write complete frame
                    long storeValue = Math.round(frameValue / TIMEFRAME_DURATION_SEC);
                    if (debug) {
                        logger.info("[CURVES_UTIL] Write last entry from {} to {} value {}", timeFrameStartTime,
                                timeFrameEndTime, storeValue);
                        System.out.println("[CURVES_UTIL] Write last entry from " + timeFrameStartTime + " to "
                                + timeFrameEndTime + " value " + storeValue);
                    }
                    QuantityType<?> state = QuantityType.valueOf(storeValue, calculationUnit);
                    normalizedMap.put(timeFrameStartTime, state);
                    normalizedMap.put(timeFrameEndTime, state);
                } else {
                    // just finish previous frame with previous value
                    if (debug) {
                        logger.info("[CURVES_UTIL] No new value for last frame - finish start frame with last entry {}",
                                normalizedMap.lastEntry());
                        System.out.println(
                                "[CURVES_UTIL] No new value for last frame - finish start frame with last entry "
                                        + normalizedMap.lastEntry());
                    }
                    normalizedMap.put(timeFrameStartTime, normalizedMap.lastEntry().getValue());
                }
                // last value - end loop
                break;
            } else {
                if (debug) {
                    logger.info("[CURVES_UTIL] Investigate Entry from {} to {} : {}", currentEntry.getKey(),
                            nextEntry.getKey(), currentEntry.getValue());
                    System.out.println("[CURVES_UTIL] Investigate Entry from " + currentEntry.getKey() + " to "
                            + nextEntry.getKey() + " : " + currentEntry.getValue());
                }

                // calculate startpoint of duration
                Instant startDuration = currentEntry.getKey();
                if (currentEntry.getKey().isBefore(timeFrameStartTime)) {
                    // if entry started before timeframe start calculation shall be done from starting of timeframe
                    startDuration = timeFrameStartTime;
                }
                if (timeUtils.isBeforeOrEqual(nextEntry.getKey(), timeFrameEndTime)) {
                    // case 1: time frame belongs completely into normalized time frame
                    double portion = currentValue.doubleValue()
                            * Duration.between(startDuration, nextEntry.getKey()).toSeconds();
                    if (debug) {
                        logger.info("[CURVES_UTIL] Case 1: entry contained in timeframe - adding portion {}", portion);
                        System.out.println(
                                "[CURVES_UTIL] Case 1: entry contained in timeframe - adding portion " + portion);
                    }
                    frameValue += portion;
                    // continue looping with next value, timeframe stays
                    currentEntry = nextEntry;
                    nextEntry = sourceMap.higherEntry(currentEntry.getKey());
                } else {
                    if (debug) {
                        logger.info(
                                "[CURVES_UTIL] Case 2: entry ends out of timeframe - claculate and put value into map");
                        System.out.println(
                                "[CURVES_UTIL] Case 2: entry ends out of timeframe - claculate and put value into map");
                    }
                    // case 2: time frame lasts over boundaries take only party into new value and continue stepping
                    frameValue += currentValue.doubleValue()
                            * Duration.between(startDuration, timeFrameEndTime).toSeconds();
                    long storeValue = Math.round(frameValue / TIMEFRAME_DURATION_SEC);
                    System.out.println("[CURVES_UTIL] Value calc: " + currentValue.doubleValue() + " duration "
                            + Duration.between(startDuration, timeFrameEndTime).toSeconds() + " frame size "
                            + TIMEFRAME_DURATION_SEC);
                    if (debug) {
                        logger.info("[CURVES_UTIL] Put value {} at position {}", storeValue, timeFrameStartTime);
                        System.out.println(
                                "[CURVES_UTIL] Put value " + storeValue + " at position " + timeFrameStartTime);
                    }
                    QuantityType<?> state = QuantityType.valueOf(storeValue, calculationUnit);
                    normalizedMap.put(timeFrameStartTime, state);

                    // start new timeframe calculation
                    if (!method.equals(CalculationMethod.ACCUMULATE)) {
                        frameValue = 0;
                    }
                    timeFrameStartTime = timeFrameEndTime;
                    timeFrameEndTime = timeFrameStartTime.plus(TIMEFRAME_DURATION_SEC, ChronoUnit.SECONDS);
                }
            }
        }
        return normalizedMap;
    }
}
