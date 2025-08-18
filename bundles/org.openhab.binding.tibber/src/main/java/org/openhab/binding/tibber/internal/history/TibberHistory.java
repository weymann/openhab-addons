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

import static org.openhab.binding.tibber.internal.TibberBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.tibber.internal.Utils;
import org.openhab.binding.tibber.internal.handler.TibberHandler;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link TibberHistory} fetches history data from the Tibber API
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TibberHistory {

    /**
     * The {@link TimeWindow} enum defines the different time windows for fetching history data. It matches the names
     * and update counts between Tibber API and channel names plus possibility to fetch all data with a full update or
     * partial update of linked items.
     */
    public enum TimeWindow {
        ANNUAL() {
            @Override
            public String channelPrefix() {
                return "yearly";
            }

            @Override
            public int getFetchCount() {
                return 1;
            }

            @Override
            public int daysInWindow() {
                return 365;
            }
        },

        MONTHLY() {

            @Override
            public String channelPrefix() {
                return "monthly";
            }

            @Override
            public int getFetchCount() {
                return 12;
            }

            @Override
            public int daysInWindow() {
                return getFetchCount() * 31;
            }
        },

        WEEKLY() {

            @Override
            public String channelPrefix() {
                return "weekly";
            }

            @Override
            public int getFetchCount() {
                return 52;
            }

            @Override
            public int daysInWindow() {
                return getFetchCount() * 7;
            }
        },

        DAILY() {

            @Override
            public String channelPrefix() {
                return "daily";
            }

            @Override
            public int getFetchCount() {
                return 31;
            }

            @Override
            public int daysInWindow() {
                return getFetchCount();
            }
        };

        public String channelPrefix() {
            return "";
        }

        public int getFetchCount() {
            return 0;
        }

        public int daysInWindow() {
            return 0;
        }

        public TimeWindow fullUpdate() {
            fullUpdate = true;
            return this;
        }

        public TimeWindow partialUpdate() {
            fullUpdate = false;
            return this;
        }

        public boolean isFullUpdate() {
            return fullUpdate;
        }

        @Override
        public String toString() {
            return name() + "" + (fullUpdate ? " full update" : " partial update");
        }

        private boolean fullUpdate = false;
    }

    private final Logger logger = LoggerFactory.getLogger(TibberHistory.class);
    private final List<String> templates = List.of(CONSUMPTION_QUERY, PRODUCTION_QUERY);
    private List<TibberHistoryListener> listeners = new ArrayList<>();
    private List<TimeWindow> workingList = new ArrayList<>();
    private boolean disposed = true;
    private Storage<String> store;
    private TibberHandler handler;
    private String homeid;

    public static ScheduledExecutorService historyScheduler = ThreadPoolManager
            .getPoolBasedSequentialScheduledExecutorService("TibberHistory", "");

    public TibberHistory(StorageService storageService, String homeid, TibberHandler handler) {
        this.handler = handler;
        this.homeid = homeid;
        store = storageService.getStorage(TibberHistory.class.getName());
        listeners.add(handler);
    }

    public void dispose(boolean disposed) {
        this.disposed = disposed;
        if (disposed) {
            synchronized (workingList) {
                workingList.clear();
            }
        }
    }

    public void updateHistory(TimeWindow window) {
        synchronized (workingList) {
            if (workingList.contains(window)) {
                logger.info("{} already requested", window.toString());
                return;
            } else {
                logger.info("Request {}", window.toString());
                workingList.add(window);
                historyScheduler.execute(this::getHistory);
            }
        }
    }

    public TibberHistorySeries getStoredSeries(TimeWindow window) {
        return new TibberHistorySeries(store.get(homeid + "-" + window.name()));
    }

    /**
     * Gets the history data for production and consumption
     *
     * @param start
     */
    private void getHistory() {
        synchronized (workingList) {
            if (workingList.isEmpty()) {
                logger.info("No history windows to process");
                return;
            } else {
                logger.info("Continue with {} from {} waiting reuqests", workingList.get(0).toString(),
                        workingList.size());
            }
        }
        TimeWindow localWorkingWindow = workingList.get(0);
        listeners.forEach(listener -> {
            listener.historyUpdated(localWorkingWindow, null);
        });
        if (localWorkingWindow != null) {
            logger.info("Fetching {}", localWorkingWindow.toString());
            TibberHistorySeries series = getStoredSeries(localWorkingWindow);
            boolean fullFetch = series.isEmpty();
            templates.forEach(template -> {
                logger.info("Fetch {}", localWorkingWindow.toString());
                String cursor = EMPTY_VALUE;
                boolean hasNext = false;
                int retryCounter = 0;
                do {
                    String consumptioQueryTemplate = Utils.getTemplate(template);
                    String consumptioQueryTemplateFilled = String.format(consumptioQueryTemplate, homeid,
                            localWorkingWindow.name(), localWorkingWindow.getFetchCount(), cursor);
                    String response = request(consumptioQueryTemplateFilled);
                    if (response != null) {
                        JsonObject jsonResponse = (JsonObject) JsonParser.parseString(response);
                        // get history array
                        String[] jsonPath = CONSUMPTION_QUERY.equals(template) ? HISTORY_CONSUMPTION_JSON_PATH
                                : HISTORY_PRODUCTION_JSON_PATH;
                        JsonObject consumptionObject = Utils.getJsonObject(jsonResponse, jsonPath);
                        JsonArray array = consumptionObject.getAsJsonArray("edges");
                        series.addData(array);

                        // check pageInfo for next page
                        JsonObject pageInfo = consumptionObject.getAsJsonObject("pageInfo");
                        hasNext = pageInfo.get("hasPreviousPage").getAsBoolean();
                        if (hasNext) {
                            cursor = "before: \\\"" + pageInfo.get("startCursor").getAsString() + "\\\"";
                        } else {
                            cursor = EMPTY_VALUE;
                        }
                        logger.info("Next: {} Cursor: {}", hasNext, cursor);
                    } else {
                        logger.warn("No response for template {}", consumptioQueryTemplateFilled);
                        hasNext = ++retryCounter < 3; // retry up to 3 times;
                    }
                    System.out.println(hasNext + " " + fullFetch + " " + retryCounter);
                    try {
                        Thread.sleep(Utils.dynamicRetryTimeMs(retryCounter));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while waiting for retry: {}", e.getMessage());
                    }
                    logger.info("Fetched so far {}", series.size());
                    // update as long as there's a next page, not disposed and fullFetch is reuqested
                } while (hasNext && fullFetch && !disposed);
            });
            store.put(homeid + "-" + localWorkingWindow.name(), series.toString());
            synchronized (workingList) {
                workingList.remove(0);
            }
            listeners.forEach(listener -> {
                listener.historyUpdated(localWorkingWindow, series);
            });
            System.out.println("All listeners notified for " + localWorkingWindow.toString());
            logger.info("{} pending requests, next is {}", workingList.size(), workingList.get(0));
        } else {
            logger.warn("No working window set, cannot fetch history");
        }
    }

    private @Nullable String request(String body) {
        Request request = handler.getRequest();
        String content = String.format(QUERY_CONTAINER, body);
        logger.trace("Query with body {}", content);
        request.content(new StringContentProvider(content, "utf-8"));
        try {
            ContentResponse cr = request.timeout(2000, TimeUnit.MILLISECONDS).send();
            int responseStatus = cr.getStatus();
            String responseString = cr.getContentAsString();
            if (responseStatus == HttpStatus.OK_200) {
                return responseString;
            } else {
                logger.warn("Error fetching history data: {} - {}", responseStatus, responseString);
            }
        } catch (Exception e) {
            logger.warn("Error fetching history data: {}", e.getMessage());
        }
        return null;
    }
}
