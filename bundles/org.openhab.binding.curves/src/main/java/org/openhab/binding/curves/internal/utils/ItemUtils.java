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

import static org.openhab.binding.curves.internal.CurvesConstants.KILOWATT_UNIT;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistenceItemInfo;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.TimeSeries.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ItemUtils} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ItemUtils {
    private final Logger logger = LoggerFactory.getLogger(ItemUtils.class);

    private PersistenceServiceRegistry persistenceServiceRegistry;
    private TimeZoneProvider timeZoneProvider;
    private ItemRegistry itemRegistry;

    public ItemUtils(ItemRegistry itemRegistry, PersistenceServiceRegistry persistenceServiceRegistry,
            TimeZoneProvider timeZoneProvider) {
        this.itemRegistry = itemRegistry;
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.timeZoneProvider = timeZoneProvider;
    }

    public Optional<GenericItem> getGenericItem(String name) {
        Item item = itemRegistry.get(name);
        if (item instanceof GenericItem genericItem) {
            return Optional.of(genericItem);
        }
        return Optional.empty();
    }

    public Instant getDateTimeTypeAsIsntant(String itemName) {
        Optional<GenericItem> dateTimeItem = getGenericItem(itemName);
        if (dateTimeItem.isPresent()) {
            State state = dateTimeItem.get().getState();
            if (state instanceof DateTimeType dateTimeState) {
                return dateTimeState.getInstant();
            } else {
                logger.warn("Item {} is not a DateTimeType: {}", itemName, state.getClass());
            }
        } else {
            logger.warn("Item {} not found", itemName);
        }
        return Instant.MAX; // return MAX to indicate no sunrise available
    }

    public QueryablePersistenceService identifyPersistenceService(String item, Instant end) {
        final Map<QueryablePersistenceService, PersistenceItemInfo> serviceList = new HashMap<>();
        // persistenceServiceRegistry.
        persistenceServiceRegistry.getAll().forEach(service -> {
            boolean checkMark = true;
            PersistenceItemInfo itemInfo = null;
            // check if persistence can be queried
            if (service instanceof QueryablePersistenceService qps) {
                logger.debug("[CURVES_ITEM] Persistence candidate {} is valid", service.getId());
                Set<PersistenceItemInfo> itemInfos = qps.getItemInfo();
                boolean itemFound = false;
                Iterator<PersistenceItemInfo> infoIterator = itemInfos.iterator();
                while (infoIterator.hasNext()) {
                    PersistenceItemInfo investgateInfo = infoIterator.next();
                    if (item.equals(investgateInfo.getName())) {
                        logger.debug("[CURVES_ITEM] Item {} found in persistence {}", item, service.getId());
                        itemInfo = investgateInfo;
                        itemFound = true;
                        break;
                    }
                }
                if (!itemFound) {
                    logger.debug("[CURVES_ITEM] Item {} not found in persistence {}", item, service.getId());
                    checkMark = itemFound;
                }
            } else {
                checkMark = false;
                logger.debug("[CURVES_ITEM] Persistence {} cannot be queried", service.getId());
            }

            // check if persistence supports forecast
            // if (end.isAfter(Instant.now())) {
            // logger.debug("[CURVES_ITEM] Forecast query till {}", end);
            // List<PersistenceStrategy> strategies = service.getDefaultStrategies();
            // logger.debug("[CURVES_ITEM] Found trategies {} for {}", strategies, service.getId());
            // if (strategies.contains(PersistenceStrategy.Globals.FORECAST)) {
            // logger.debug("[CURVES_ITEM] {} supports forecasts", service.getId());
            // } else {
            // logger.debug("[CURVES_ITEM] No Forecast strategy available for {}", service.getLabel(null));
            // checkMark = false;
            // }
            // } else {
            // logger.debug("[CURVES_ITEM] Historic query till {}", end);
            // }
            if (checkMark) {
                if (itemInfo != null) {
                    serviceList.put((QueryablePersistenceService) service, itemInfo);
                }
            }
        });
        logger.info("[CURVES_ITEM] Persistency services {} for {} ", serviceList, item);
        QueryablePersistenceService toSelect = null;
        PersistenceItemInfo previousInfo = null;
        if (serviceList.isEmpty()) {
            logger.error("[CURVES_ITEM] No services found for {} ", item);
        } else if (serviceList.size() > 1) {
            logger.warn("[CURVES_ITEM] Several services {} for {} ", serviceList, item);
        }

        if (!serviceList.isEmpty()) {
            Iterator<QueryablePersistenceService> entryIter = serviceList.keySet().iterator();
            while (entryIter.hasNext()) {
                QueryablePersistenceService investigateService = entryIter.next();
                if (toSelect == null) {
                    toSelect = investigateService;
                    previousInfo = serviceList.get(toSelect);
                    logger.debug("[CURVES_ITEM] Select {} as return value", investigateService);
                } else {
                    PersistenceItemInfo investigateInfo = serviceList.get(investigateService);
                    if (investigateInfo.getLatest().after(previousInfo.getLatest())) {
                        logger.debug("[CURVES_ITEM] Select {} as return value - fresher values than {}",
                                investigateService, toSelect);
                        toSelect = investigateService;
                        previousInfo = investigateInfo;
                    } else {
                        logger.debug("[CURVES_ITEM] Stay with {} as return value - older values than {}",
                                investigateService, toSelect);
                        previousInfo = investigateInfo;
                    }
                }
            }
        }
        if (toSelect == null) {
            throw new RuntimeException("No Persistence found for item " + item);
        } else {
            return toSelect;
        }
    }

    public TimeSeries getHistoricTimeSeries(String itemName, Instant begin, Instant end) {
        QueryablePersistenceService persistence = identifyPersistenceService(itemName, end);
        TimeSeries historicSeries = new TimeSeries(Policy.REPLACE);
        long startCalculation = System.currentTimeMillis();
        ZonedDateTime beginPeriodDT = begin.atZone(timeZoneProvider.getTimeZone());
        ZonedDateTime endPeriodDT = end.atZone(timeZoneProvider.getTimeZone());
        logger.info("[CURVES_ITEM] GET historic items ftom {} to {} for {} from {}", beginPeriodDT, endPeriodDT,
                itemName, persistence.getId());

        FilterCriteria fc = new FilterCriteria();
        fc.setBeginDate(beginPeriodDT);
        fc.setEndDate(endPeriodDT);
        fc.setItemName(itemName);

        logger.debug("History for {} from {} till {}", itemName, beginPeriodDT, endPeriodDT);
        Iterable<HistoricItem> historicItems = persistence.query(fc);
        // int i = 0;
        for (HistoricItem historicItem : historicItems) {
            ZonedDateTime stateTimestamp = historicItem.getTimestamp();
            State state = historicItem.getState();
            historicSeries.add(stateTimestamp.toInstant(), state);
            // if (i < 100) {
            // logger.debug("{} - {}", stateTimestamp, state);
            // }
            // i++;
        }
        long calculationDuration = System.currentTimeMillis() - startCalculation;
        logger.info("[CURVES_ITEM] GOT {} historic items ftom {} to {} for {} ", historicSeries.size(),
                historicSeries.getBegin(), historicSeries.getEnd(), itemName);
        logger.info("[CURVES_ITEM] Historic timeseries calculation took {} for {} elemens", calculationDuration,
                itemName);
        return historicSeries;
    }

    private double calcuateKwh(Instant begin, Instant end, double power) {
        long durationSeconds = Duration.between(begin, end).getSeconds();
        return power * durationSeconds / 3600;
    }

    public double getEnergyTillNow(String itemName) {
        long startCalculation = System.currentTimeMillis();

        ZonedDateTime beginPeriodDT = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endPeriodDT = ZonedDateTime.now();
        FilterCriteria fc = new FilterCriteria();
        fc.setBeginDate(beginPeriodDT);
        fc.setEndDate(endPeriodDT);
        fc.setItemName(itemName);
        Iterable<HistoricItem> historicItems = identifyPersistenceService(itemName, endPeriodDT.toInstant()).query(fc);
        double total = 0;
        double lastPowerValue = -1;
        Instant lastTimeStamp = Instant.MAX;
        for (HistoricItem historicItem : historicItems) {
            State powerState = historicItem.getState();
            if (powerState instanceof QuantityType<?> qs) {
                QuantityType<Power> powerKWState = (QuantityType<Power>) qs.toInvertibleUnit(KILOWATT_UNIT);
                if (powerKWState != null) {
                    lastPowerValue = powerKWState.doubleValue();
                } else {
                    logger.trace("Cannot convert Unit {} to {}", qs.getUnit(), KILOWATT_UNIT);
                    return 0;
                }
            } else {
                logger.info("Power item {} has no unit. Skip Energy calculation!", itemName);
                return 0;
            }
            ZonedDateTime stateTimestamp = historicItem.getTimestamp();
            if (lastTimeStamp.isBefore(stateTimestamp.toInstant()) && lastPowerValue >= 0) {
                total += calcuateKwh(lastTimeStamp, stateTimestamp.toInstant(), lastPowerValue);
            } else {
                logger.info("Skip timestamp {}", stateTimestamp);
            }
            lastTimeStamp = stateTimestamp.toInstant();
        }
        if (lastTimeStamp.isBefore(endPeriodDT.toInstant()) && lastPowerValue >= 0) {
            total += calcuateKwh(lastTimeStamp, endPeriodDT.toInstant(), lastPowerValue);
        }
        long calculationDuration = System.currentTimeMillis() - startCalculation;
        logger.info("Total power till now in kWh {} took {} ms", total, calculationDuration);
        return total;
    }
}
