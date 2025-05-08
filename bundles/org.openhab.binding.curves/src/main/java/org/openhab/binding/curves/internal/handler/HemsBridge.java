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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.curves.internal.calculator.BaseCurves;
import org.openhab.binding.curves.internal.calculator.CurveCalculationResult;
import org.openhab.binding.curves.internal.calculator.DataLake;
import org.openhab.binding.curves.internal.config.HemsConfiguration;
import org.openhab.binding.curves.internal.interfaces.Battery;
import org.openhab.binding.curves.internal.interfaces.Battery.BatteryProperty;
import org.openhab.binding.curves.internal.interfaces.CurvesEntity;
import org.openhab.binding.curves.internal.interfaces.Grid;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Household;
import org.openhab.binding.curves.internal.interfaces.PV;
import org.openhab.binding.curves.internal.interfaces.PV.Type;
import org.openhab.binding.curves.internal.interfaces.PV.Unit;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.ItemUtils;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HemsBridge} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class HemsBridge extends BaseBridgeHandler implements Hems {

    private final Logger logger = LoggerFactory.getLogger(HemsBridge.class);
    private final ScheduledExecutorService sequentialScheduler;

    private @Nullable ScheduledFuture<?> refreshJob;
    private Map<String, List<CurvesEntity>> entityMap = new HashMap<>();
    private List<ControllerCommand> commandQueue = new ArrayList<>();
    private TimeZoneProvider timeZoneProvider;
    private CurveUtils curveUtils;
    private ItemUtils itemUtils;
    private TimeUtils timeUtils;
    private DataLake dataLake;
    private int handle = 0;

    protected HemsConfiguration config;

    public HemsBridge(Bridge bridge, ItemRegistry itemRegistry, PersistenceServiceRegistry persistenceServiceRegistry,
            TimeZoneProvider timeZoneProvider) {
        super(bridge);
        logger.info("CURVES_CONTROLLER Handler constructor");
        this.timeZoneProvider = timeZoneProvider;

        config = getConfigAs(HemsConfiguration.class);
        itemUtils = new ItemUtils(itemRegistry, persistenceServiceRegistry, timeZoneProvider);
        timeUtils = new TimeUtils(timeZoneProvider, Duration.ofMinutes(config.granularity));
        curveUtils = new CurveUtils(Duration.ofMinutes(config.granularity), timeUtils);
        dataLake = new DataLake(this, config);
        sequentialScheduler = ThreadPoolManager
                .getPoolBasedSequentialScheduledExecutorService(this.getClass().getName(), BINDING_ID);
    }

    /**
     * ThingHandler overrides
     */

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(HemsConfiguration.class);
        curveUtils = new CurveUtils(Duration.ofMinutes(config.granularity), timeUtils);
        timeUtils = new TimeUtils(timeZoneProvider, Duration.ofMinutes(config.granularity));
        updateStatus(ThingStatus.UNKNOWN);

        logger.info("CURVES_CONTROLLER start scheduler");
        // todo check for triggers and remove time based updates
        refreshJob = sequentialScheduler.scheduleWithFixedDelay(this::update, 0, 15, TimeUnit.MINUTES);
    }

    @Override
    public void dispose() {
        logger.info("CURVES_CONTROLLER dispose");
        super.dispose();
        ScheduledFuture<?> localJob = refreshJob;
        if (localJob != null) {
            localJob.cancel(false);
            refreshJob = null;
        }
    }

    /**
     * EMS overrides
     */

    @Override
    public Duration getGranularity() {
        return Duration.ofMinutes(config.granularity);
    }

    @Override
    public CurveUtils getCurveUtils() {
        return curveUtils;
    }

    @Override
    public ItemUtils getItemUtils() {
        return itemUtils;
    }

    @Override
    public TimeUtils getTimeUtils() {
        return timeUtils;
    }

    public DataLake datalake() {
        return dataLake;
    }

    @Override
    public void registerEntity(CurvesEntity entity) {
        String interfaceName = getSupportedInterface(entity);
        synchronized (entityMap) {
            List<CurvesEntity> typeList = entityMap.get(interfaceName);
            if (typeList == null) {
                typeList = new ArrayList<>();
                entityMap.put(interfaceName, typeList);
            }
            typeList.add(entity);
        }
    }

    @Override
    public void deregisterEntity(CurvesEntity entity) {
        String interfaceName = getSupportedInterface(entity);
        synchronized (entityMap) {
            List<CurvesEntity> typeList = entityMap.get(interfaceName);
            if (typeList != null) {
                typeList.remove(entity);
            } // else nothing
        }
    }

    @Override
    public long handle(CurvesEntity entity, ControllerCommand command) {
        synchronized (commandQueue) {
            commandQueue.add(command);
        }
        return ++handle;
    }

    /**
     * Helpers
     */

    public List<CurvesEntity> getAllEntities() {
        List<CurvesEntity> allEntityList = new ArrayList<>();
        Iterator<String> keyIter = entityMap.keySet().iterator();
        while (keyIter.hasNext()) {
            List<CurvesEntity> entityList = entityMap.get(keyIter.next());
            if (entityList != null) {
                for (Iterator<CurvesEntity> iterator = entityList.iterator(); iterator.hasNext();) {
                    allEntityList.add(iterator.next());
                }
            }
        }
        return allEntityList;
    }

    private String getSupportedInterface(CurvesEntity entity) {
        String className = "";
        List<Class> interfaceList = Arrays.asList(entity.getClass().getInterfaces());
        for (Iterator<Class> iterator = interfaceList.iterator(); iterator.hasNext();) {
            Class investigatedClass = iterator.next();
            if (SUPPORTED_INTERFACES.contains(investigatedClass)) {
                if (className.isBlank()) {
                    className = investigatedClass.getSimpleName();
                }
            }
        }
        return className;
    }

    /**
     * calculations
     */

    private void update() {
        System.out.println("CURVES_CONTROLLER update");

        try {
            logger.info("CURVES_CONTROLLER Update");
            long startTime = System.currentTimeMillis();
            synchronized (commandQueue) {
                BaseCurves baseCurves = fetchData();
                CurveCalculationResult result = new CurveCalculationResult(baseCurves, this);
                result.calculateCurves(List.of());
            }
            long duration = System.currentTimeMillis() - startTime;
            logger.info("CURVES_CONTROLLER Update took {} ms", duration);
        } catch (Exception e) {
            logger.warn("CURVES_CONTROLLER Excpetion {} during update", e.getMessage());
            StackTraceElement[] stack = e.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                logger.debug("CURVES_CONTROLLER {}", stack[i]);
            }
        }
    }

    /**
     * Fetches all data from the entities and creates a BaseCurves object containing household, solar forecast and price
     * curve
     *
     * @return BaseCurves object containing all data needed for calculation
     */
    private BaseCurves fetchData() {
        BaseCurves baseCurves = new BaseCurves(DataLake.getInstance(getUID()));
        List<CurvesEntity> entityList = entityMap.get(PV.class.getSimpleName());
        if (entityList != null) {
            if (!entityList.isEmpty()) {
                for (Iterator<CurvesEntity> iterator = entityList.iterator(); iterator.hasNext();) {
                    CurvesEntity curvesEntity = iterator.next();
                    if (curvesEntity instanceof PV pvEntity) {
                        baseCurves.addForecast(pvEntity.getPredictionMap(Type.FORECAST, Unit.POWER));
                    }
                    if (curvesEntity instanceof Household householdEntity) {
                        baseCurves.addHousehold(householdEntity.getPredictionMap());
                    }
                    if (curvesEntity instanceof Grid gridEntitiy) {
                        baseCurves.setPrices(gridEntitiy.getPriceMap());
                    }
                }
            }
        }
        return baseCurves;
    }

    private Instant[] getPVProductionPeriod(BaseCurves baseCurves, int ahead) {
        Instant startOfToday = timeUtils.startOfDay(ahead); // end of today is begin of tomorrow
        Instant endOfToday = timeUtils.startOfDay(ahead + 1); // end of today is begin of tomorrow
        Instant startOfProduction = Instant.MAX;
        Instant endOfProduction = Instant.MIN;
        Instant stepperTime = startOfToday;
        logger.trace("[CURVES_PROD_PER] Get production period {}", baseCurves.forecastMap);
        logger.trace("[CURVES_PROD_PER] Available data from {} to {}", baseCurves.forecastMap.firstEntry().getKey(),
                baseCurves.forecastMap.lastEntry().getKey());
        while (stepperTime.isBefore(endOfToday)) {
            logger.trace("[CURVES_PROD_PER] start at {}", stepperTime);
            QuantityType<?> powerNow = baseCurves.forecastMap.ceilingEntry(stepperTime).getValue();
            if (powerNow.doubleValue() > 0 && Instant.MAX.equals(startOfProduction)) {
                // if start time wasn't found yet set it
                logger.trace("PV Production starts {}", stepperTime);
                startOfProduction = stepperTime;
            }
            if (!Instant.MAX.equals(startOfProduction) && Instant.MIN.equals(endOfProduction)
                    && powerNow.doubleValue() == 0) {
                // if start time was found and production goes to zero endtime is found
                logger.trace("[CURVES_PROD_PER] PV Production ends {}", stepperTime);
                endOfProduction = stepperTime;
            }
            if (!Instant.MAX.equals(startOfProduction) && !Instant.MIN.equals(endOfProduction)
                    && powerNow.doubleValue() > 0) {
                // start and end time were calculated but still non null value was found
                // maybe the case forecast drops to 0 during suntime, unlikely but possible
                // find new end time!
                logger.trace("[CURVES_PROD_PER] PV Production reset end time");
                endOfProduction = Instant.MIN;
            }
            stepperTime = stepperTime.plusSeconds(getGranularity().toSeconds());
            logger.trace("[CURVES_PROD_PER] next at {}", stepperTime);
        }
        return new Instant[] { startOfProduction, endOfProduction };
    }

    public Number getBatteryProperty(BatteryProperty prop) {
        double property = 0;
        List<CurvesEntity> entityList = entityMap.get(Battery.class.getSimpleName());
        if (entityList != null) {
            for (Iterator<CurvesEntity> iterator = entityList.iterator(); iterator.hasNext();) {
                Battery batteryEntity = (Battery) iterator.next();
                switch (prop) {
                    case MAX_CHARGE_CAPABILITY:
                        property += batteryEntity.getMaximumChargePower();
                        break;
                    case MAX_DISCHARGE_CAPABILITY:
                        property += batteryEntity.getMaximumDischargePower();
                        break;
                    case CAPACITY:
                        property += batteryEntity.getCapacity();
                        break;
                    case CHARGE:
                        property += batteryEntity.getCharge();
                        break;
                    case SOC:
                        if (entityList.size() > 1) {
                            logger.info("CURVES_CONTROLLER SoC with {} batteries doesn't makes sense",
                                    entityList.size());
                        }
                        property += batteryEntity.getStateOfCharge();
                        break;
                }
            }
            // if list is empty no battery is attached to the PV system
            // returning 0 ensures no battery capacity -> no charge / discharge will be calculated!
        }
        return property;
    }

    @Override
    public String getUID() {
        return getThing().getUID().getAsString();
    }
}
