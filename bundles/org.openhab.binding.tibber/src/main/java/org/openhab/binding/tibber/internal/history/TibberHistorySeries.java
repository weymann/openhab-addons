/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.tibber.internal.history;

import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.tibber.internal.Utils;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.CurrencyUnits;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link TibberHistorySeries} provides storage for energy consumption, cost and production
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TibberHistorySeries extends TreeMap<Instant, JsonObject> {

    private static final long serialVersionUID = -6635491873715495757L;
    private final Logger logger = LoggerFactory.getLogger(TibberHistorySeries.class);

    public static final String DATE_TIME = "dateTime";
    public static final String DATA = "data";
    public static final String PURPOSE_CONSUMPTION = "consumption";
    public static final String PURPOSE_COST = "cost";
    public static final String PURPOSE_PRODUCTION = "production";

    public TibberHistorySeries(@Nullable String storedData) {
        if (storedData != null) {
            try {
                JsonArray storedArray = (JsonArray) JsonParser.parseString(storedData);
                storedArray.forEach(entry -> {
                    Instant key = Instant.parse(entry.getAsJsonObject().get(DATE_TIME).getAsString());
                    JsonObject value = entry.getAsJsonObject().get(DATA).getAsJsonObject();
                    put(key, value);
                });
            } catch (JsonSyntaxException jse) {
                logger.warn("Couldn't parse store history data {}", storedData);
            }
        }
    }

    public void addData(JsonArray data) {
        data.forEach(element -> {
            JsonObject entry = element.getAsJsonObject();
            JsonObject arrayEntry = entry.get("node").getAsJsonObject();
            String startTime = arrayEntry.get("from").getAsString();
            Instant key = Instant.parse(startTime);
            JsonObject historyDataElement = get(key);
            if (historyDataElement == null) {
                historyDataElement = new JsonObject();
            }
            if (arrayEntry.has(PURPOSE_CONSUMPTION)) {
                QuantityType<?> consumptionState = QuantityType
                        .valueOf(arrayEntry.get(PURPOSE_CONSUMPTION).getAsString() + " "
                                + arrayEntry.get("consumptionUnit").getAsString());
                historyDataElement.addProperty(PURPOSE_CONSUMPTION, consumptionState.toFullString());
            }
            if (arrayEntry.has(PURPOSE_COST)) {
                State costState;
                Unit<?> currencyUnit = CurrencyUnits.getInstance().getUnit(arrayEntry.get("currency").getAsString());
                if (currencyUnit != null) {
                    costState = QuantityType.valueOf(
                            arrayEntry.get("cost").getAsString() + " " + arrayEntry.get("currency").getAsString());
                } else {
                    costState = DecimalType.valueOf(arrayEntry.get("cost").getAsString());
                }
                historyDataElement.addProperty(PURPOSE_COST, costState.toFullString());
            }
            if (arrayEntry.has(PURPOSE_PRODUCTION)) {
                QuantityType<?> productionState = QuantityType.valueOf(arrayEntry.get(PURPOSE_PRODUCTION).getAsString()
                        + " " + arrayEntry.get("productionUnit").getAsString());
                historyDataElement.addProperty(PURPOSE_PRODUCTION, productionState.toFullString());
            }
            put(key, historyDataElement);

        });
    }

    public TimeSeries getTimeSeries(Instant start, String purpose) {
        TimeSeries series = new TimeSeries(TimeSeries.Policy.REPLACE);
        SortedMap<Instant, JsonObject> partMap = this.tailMap(start);
        partMap.forEach((time, historyElement) -> {
            if (historyElement.has(purpose)) {
                series.add(time, QuantityType.valueOf(historyElement.get(purpose).getAsString()));
            }
        });
        return series;
    }

    @Override
    public String toString() {
        JsonArray array = new JsonArray();
        forEach((time, historyElement) -> {
            JsonObject elem = new JsonObject();
            elem.addProperty(DATE_TIME, time.toString());
            elem.add(DATA, historyElement);
            array.add(elem);
        });
        return Utils.GSON.toJson(array);
    }
}
