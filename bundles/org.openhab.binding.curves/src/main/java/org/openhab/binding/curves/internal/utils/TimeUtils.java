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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.TimeZoneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TimeUtils} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class TimeUtils {
    private final Logger logger = LoggerFactory.getLogger(TimeUtils.class);
    private TimeZoneProvider timeZoneProvider;
    private Clock clock = Clock.systemDefaultZone();
    private Duration granularity;

    public TimeUtils(TimeZoneProvider timeZoneProvider, Duration duration) {
        this.timeZoneProvider = timeZoneProvider;
        this.granularity = duration;
    }

    /**
     * Only for unit testing setting a fixed clock with desired date-time
     *
     * @param c
     */
    public void setClock(Clock c) {
        clock = c;
    }

    public void setTimeZoneProvider(TimeZoneProvider tzp) {
        timeZoneProvider = tzp;
    }

    public Clock getClock() {
        return clock.withZone(timeZoneProvider.getTimeZone());
    }

    public Instant timeFrame(Instant timestamp, int offset) {
        ZonedDateTime zdt = timestamp.atZone(timeZoneProvider.getTimeZone());
        int granularityMinutes = (int) granularity.toMinutes();
        int startPointMultiplier = Math.round(zdt.getMinute() / granularityMinutes) + offset;
        int minutes = startPointMultiplier * granularityMinutes;
        ZonedDateTime startTime = zdt.truncatedTo(ChronoUnit.HOURS).plusMinutes(minutes)
                .truncatedTo(ChronoUnit.MINUTES);
        return startTime.toInstant();
    }

    public boolean isBeforeOrEqual(Instant input, Instant reference) {
        return !input.isAfter(reference);
    }

    public Instant now() {
        return Instant.now(getClock());
    }

    public Instant startOfDay(int dayOffset) {
        return LocalDate.now(getClock()).atStartOfDay().atZone(timeZoneProvider.getTimeZone()).plusDays(dayOffset)
                .toInstant();
    }

    public int getDay() {
        return now().atZone(timeZoneProvider.getTimeZone()).getDayOfMonth();
    }
}
