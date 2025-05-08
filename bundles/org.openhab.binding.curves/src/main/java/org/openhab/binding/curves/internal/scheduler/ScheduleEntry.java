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
package org.openhab.binding.curves.internal.scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.openhab.binding.curves.internal.strategies.Strategy;

/**
 * The {@link ScheduleEntry} is one entry of a schedule
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ScheduleEntry {
    public Instant start;
    public Instant end = Instant.MIN;
    public List<Strategy> strategies = new ArrayList<>();

    public ScheduleEntry(Instant start, @Nullable Instant end, int durationInMinutes) {
        this.start = start;
        if (end != null) {
            this.end = end;
        } else {
            this.end = start.plusSeconds(durationInMinutes * 60);
        }
    }

    public void addStrategy(Strategy strategy) {
        strategies.add(strategy);
    }

    @Override
    public String toString() {
        return "Start: " + start + ", Strategies: " + (new JSONArray(strategies)).toString();
    }
}
