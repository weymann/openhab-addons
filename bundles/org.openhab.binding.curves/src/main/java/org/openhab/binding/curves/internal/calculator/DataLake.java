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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.HemsConfiguration;
import org.openhab.binding.curves.internal.exception.DataLakeException;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.interfaces.Battery.BatteryProperty;
import org.openhab.binding.curves.internal.utils.ItemUtils;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DataLake} stores internal and external data needed for classes over the binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class DataLake {
    private static final Map<String, DataLake> instances = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(DataLake.class);

    private HemsBridge controller;
    private ItemUtils itemUtils;
    private TimeUtils timeUtils;
    private long predictionGranularityMinutes = 1; // default granularity for prediction;

    public DataLake(HemsBridge controller, HemsConfiguration config) {
        this.controller = controller;
        this.itemUtils = controller.getItemUtils();
        this.timeUtils = controller.getTimeUtils();

        predictionGranularityMinutes = config.granularity;
        instances.put(controller.getUID(), this);
    }

    public static DataLake getInstance(String uid) {
        DataLake instance = instances.get(uid);
        if (instance == null) {
            throw new DataLakeException("No DataLake instance found for UID: " + uid);
        }
        return instance;
    }

    /**
     * Returns the start of the prediction period. This is the start of today.
     *
     * @return the start of the prediction period as an Instant
     */
    public Instant getPredictionStart() {
        return timeUtils.startOfDay(0);
    }

    /**
     * Returns the end of the prediction period. Typically forecast and prices are delivered for today and tomorrow. But
     * prediction shall look till sunrise after tomorrow.
     *
     * @return the end of the prediction period as an Instant
     */
    public Instant getPredictionEnd() {
        // todo check if sunrise can be calculated more precisely for the tomorrow & day after tomorrow
        return Instant.now().plus(2, ChronoUnit.DAYS);
    }

    public long predictionGranularityMinutes() {
        return predictionGranularityMinutes;
    }

    public Number getBatteryProperty(BatteryProperty prop) {
        // delegate this back to controller which may have more than BatteryEntities
        return controller.getBatteryProperty(prop);
    }
}
