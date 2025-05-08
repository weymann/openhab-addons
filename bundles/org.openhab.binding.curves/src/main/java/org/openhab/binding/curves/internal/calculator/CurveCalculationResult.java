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
package org.openhab.binding.curves.internal.calculator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.interfaces.Battery.BatteryProperty;
import org.openhab.binding.curves.internal.scheduler.ScheduleEntry;
import org.openhab.binding.curves.internal.strategies.Strategy;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The interface {@link CurveCalculationResult} is the marker interface for all strategies
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class CurveCalculationResult extends BaseCurves {
    private final Logger logger = LoggerFactory.getLogger(CurveCalculationResult.class);
    private final TimeUtils timeUtils;

    private TreeMap<Instant, State> home2GridMap = new TreeMap<>();
    private TreeMap<Instant, State> grid2HomeMap = new TreeMap<>();
    private TreeMap<Instant, State> batteryChargeMap = new TreeMap<>();
    private TreeMap<Instant, State> batteryDischargeMap = new TreeMap<>();
    private TreeMap<Instant, State> pvHouseholdDifferenceMap = new TreeMap<>();

    public enum Result {
        GREEN,
        YELLOW,
        ORANGE,
        RED
    }

    public double homeConsumption;
    public double homeConsumptionSun;
    public double homeConsumptionBattery;
    public double homeConsumptionGrid;

    public double autarky;

    public double gridConsumption;
    public double gridSupply;
    public double surplus;
    public List<Strategy> appliedStrategies = new ArrayList<>();
    public List<ScheduleEntry> schedule = List.of();

    public CurveCalculationResult(BaseCurves baseCurves, HemsBridge controller) {
        super(DataLake.getInstance(controller.getUID()));
        // copy values into super class
        super.forecastMap.putAll(baseCurves.forecastMap);
        super.householdMap.putAll(baseCurves.householdMap);
        super.priceMap.putAll(baseCurves.priceMap);
        timeUtils = controller.getTimeUtils();
    }

    public void calculateCurves(List<Strategy> strategies) {
        appliedStrategies.addAll(strategies);
        logger.info("[CURVES_CONTROLLER] calculateCurves");

        Instant actualTime = timeUtils.timeFrame(timeUtils.now(), 0);
        Instant endTime = dataLake.getPredictionEnd();

        // get actual charging state and parameters from battery
        double batteryCharge = dataLake.getBatteryProperty(BatteryProperty.CHARGE).doubleValue();
        double batteryCapacity = dataLake.getBatteryProperty(BatteryProperty.CAPACITY).doubleValue();
        double maxChargePower = powerToEnergy(
                dataLake.getBatteryProperty(BatteryProperty.MAX_CHARGE_CAPABILITY).doubleValue(), 60);
        double maxDischargePower = powerToEnergy(
                dataLake.getBatteryProperty(BatteryProperty.MAX_DISCHARGE_CAPABILITY).doubleValue(), 60);

        logger.debug("[CURVES_CALC] Battery at {} from capacity {}", batteryCharge, batteryCapacity);
        // double totalProduction = 0;
        // double totalConsumption = 0;
        // double totalCharge = 0;
        // double totalDischarge = 0;
        // double totalGridSupply = 0;
        // double totalGridConsumption = 0;
        while (actualTime.isBefore(endTime)) {
            double grid2Home = 0;
            double home2Grid = 0;
            Entry<Instant, QuantityType<?>> powerForTimeframeEntry = forecastMap.floorEntry(actualTime);
            Entry<Instant, QuantityType<?>> householdPowerForTimeframeEntry = householdMap.floorEntry(actualTime);
            double forecastForTimeframe = powerToEnergy(powerForTimeframeEntry.getValue().doubleValue());
            double consumptionEnergyForTimeframe = powerToEnergy(
                    householdPowerForTimeframeEntry.getValue().doubleValue());

            // calculate possible charge from forecast - consumption or 0 if PV cannot cover consumption
            double pvSurplus = forecastForTimeframe - consumptionEnergyForTimeframe
                    - powerAddon(strategies, actualTime);

            double charge = 0;
            double discharge = 0;
            // handle case PV is bigger than consumption
            if (pvSurplus > 0) {
                boolean chargingAllowed = isChargingAllowed(strategies, actualTime);
                // respect charge power capability of battery
                charge = chargingAllowed ? Math.min(pvSurplus, maxChargePower) : 0;
                if (chargingAllowed) {
                    if (batteryCharge + charge <= batteryCapacity) {
                        // case battery can handle charge
                        batteryCharge += charge;
                    } else {
                        // case battery is / will be fully charged, take charge portion which cannot be handled by
                        // battery and feed it to grid ...
                        home2Grid = batteryCharge + charge - batteryCapacity;
                        // only portion to fill the battery completely is taken as charge value ...
                        charge = batteryCapacity - batteryCharge;
                        // mark battery as fully charged
                        batteryCharge = batteryCapacity;
                    }
                } else {
                    // case no charging allowed everything goes to grid
                    home2Grid = pvSurplus;
                }
            }

            // handle case PV is lower than consumption
            if (pvSurplus < 0) {
                boolean dischargingAllowed = isDischargingAllowed(strategies, actualTime);
                double pvShortage = pvSurplus * -1;
                // respect charge power capability of battery
                discharge = dischargingAllowed ? Math.min(pvShortage, maxDischargePower) : 0;
                if (dischargingAllowed) {
                    if (batteryCharge - discharge >= 0) {
                        // case battery can handle discharge, adjust battery charge level
                        batteryCharge -= discharge;
                    } else {
                        // case battery cannot fully handle discharge, take power from grid ...
                        grid2Home = (batteryCharge - discharge) * -1;
                        // discharge is remaining battery capacity ...
                        discharge = batteryCharge;
                        // adjust battery charge level
                        batteryCharge = 0.0;
                    }
                } else {
                    grid2Home = pvShortage;
                }
            }
            logger.trace("[CURVES_CALC] {} PV {} CONS {} PLUS {} BAT= {} BAT+ {} BAT- {} GRID+ {} GRID- {}", actualTime,
                    forecastForTimeframe, consumptionEnergyForTimeframe, pvSurplus, batteryCharge, charge, discharge,
                    home2Grid, grid2Home);

            home2GridMap.put(actualTime, QuantityType.valueOf(energyToPower(home2Grid) + " W"));
            grid2HomeMap.put(actualTime, QuantityType.valueOf(energyToPower(grid2Home) + " W"));
            batteryChargeMap.put(actualTime, QuantityType.valueOf(energyToPower(charge) + " W"));
            batteryDischargeMap.put(actualTime, QuantityType.valueOf(energyToPower(discharge) + " W"));
            pvHouseholdDifferenceMap.put(actualTime, QuantityType.valueOf(energyToPower(pvSurplus) + " W"));
            actualTime = actualTime.plus(dataLake.predictionGranularityMinutes(), ChronoUnit.MINUTES);

            // totalProduction += energyForTimeFrame;
            // totalConsumption += consumptionEnergyForTimeframe;
            // totalCharge += charge;
            // totalDischarge += discharge;
            // totalGridSupply += gridSupply;
            // totalGridConsumption += gridConsumption;
        }
        logger.info("Surplus Map {}", pvHouseholdDifferenceMap);
    }

    private double powerToEnergy(double power) {
        return powerToEnergy(power, dataLake.predictionGranularityMinutes());
    }

    private double powerToEnergy(double power, long minutes) {
        return power * minutes / 60.0 / 1000.0;
    }

    private double energyToPower(double energy) {
        return energyToPower(energy, dataLake.predictionGranularityMinutes());
    }

    private double energyToPower(double energy, long minutes) {
        return Math.round(energy * 1000 * 60 / minutes);
    }

    /**
     * Check for all given strategies if additional power is needed
     *
     * @param strategies to check
     * @param timestamp to check
     * @return power to add as double
     */
    private double powerAddon(List<Strategy> strategies, Instant timestamp) {
        double addon = 0;
        for (Strategy strategy : strategies) {
            addon += strategy.powerAdd(timestamp);
        }
        return addon;
    }

    private boolean isChargingAllowed(List<Strategy> strategies, Instant timestamp) {
        boolean allowed = true;
        for (Strategy strategy : strategies) {
            allowed = allowed || strategy.isHomeBatteryChargingAllowed(timestamp);
        }
        return allowed;
    }

    private boolean isDischargingAllowed(List<Strategy> strategies, Instant timestamp) {
        boolean allowed = true;
        for (Strategy strategy : strategies) {
            allowed = allowed || strategy.isHomeBatteryDischargingAllowed(timestamp);
        }
        return allowed;
    }

    @Override
    public String toString() {
        return "";
    }

    public List<ScheduleEntry> getSchedule() {
        List<ScheduleEntry> schedule = new ArrayList<>();
        Instant actualTime = dataLake.getPredictionStart();
        Instant endTime = dataLake.getPredictionEnd();
        Instant iterationTime = actualTime;
        System.out.println("[CURVES_CALC] getSchedule with granularity " + dataLake.predictionGranularityMinutes());
        while (iterationTime.isBefore(endTime)) {
            ScheduleEntry entry = new ScheduleEntry(iterationTime, null, (int) dataLake.predictionGranularityMinutes());
            // check for all strategies if they are active at this timestamp
            for (Strategy strategy : appliedStrategies) {
                if (!strategy.isActive(iterationTime)) {
                    entry.addStrategy(strategy);
                }
            }
            schedule.add(entry);
            iterationTime = iterationTime.plus(dataLake.predictionGranularityMinutes(), ChronoUnit.MINUTES);
        }
        return schedule;
    }
}
