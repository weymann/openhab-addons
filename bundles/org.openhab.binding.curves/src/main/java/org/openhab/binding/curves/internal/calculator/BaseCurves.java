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
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BaseCurves} stores the curves needed to start a calculation. Household, Forecast and Prices.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BaseCurves {
    private final Logger logger = LoggerFactory.getLogger(BaseCurves.class);

    protected final DataLake dataLake;

    public TreeMap<Instant, QuantityType<?>> forecastMap = new TreeMap<>();
    public TreeMap<Instant, QuantityType<?>> householdMap = new TreeMap<>();
    public TreeMap<Instant, State> priceMap = new TreeMap<>();

    public BaseCurves(DataLake dataLake) {
        this.dataLake = dataLake;
    }

    public void addForecast(TreeMap<Instant, State> forecast) {
        addToMap(forecast, forecastMap);
        logger.info("BASE_CURVE Received PV Prediction from {} to {} with {} entries", forecastMap.firstKey(),
                forecastMap.lastKey(), forecastMap.size());
    }

    public void addHousehold(TreeMap<Instant, State> household) {
        addToMap(household, householdMap);
        logger.info("CURVES_CONTROLLER Received Household Prediction from {} to {} with {} entries",
                householdMap.firstKey(), householdMap.lastKey(), householdMap.size());
    }

    public void setPrices(TreeMap<Instant, State> prices) {
        if (priceMap.isEmpty()) {
            priceMap.putAll(prices);
        } else {
            logger.error("BASE_CURVE Prices already set, overwriting existing values");
        }
    }

    @SuppressWarnings({ "unused", "null" })
    private void addToMap(TreeMap<Instant, State> sourceMap, TreeMap<Instant, QuantityType<?>> targetMap) {
        sourceMap.forEach((key, sourceValue) -> {
            if (sourceValue instanceof QuantityType sourceQuantityType) {
                QuantityType<?> targetValue = targetMap.remove(key);
                if (targetValue != null) {
                    targetMap.put(key, targetValue.add(sourceQuantityType));
                } else {
                    targetMap.put(key, sourceQuantityType);
                }
            } else {
                logger.warn("{} isn't a QuantityType", sourceValue.toFullString());
            }
        });
    }

    public Instant getForecastEnd() {
        return forecastMap.isEmpty() ? Instant.MIN : forecastMap.lastKey();
    }

    public Instant getSpotpricesEnd() {
        return priceMap.isEmpty() ? Instant.MIN : priceMap.lastKey();
    }
}
