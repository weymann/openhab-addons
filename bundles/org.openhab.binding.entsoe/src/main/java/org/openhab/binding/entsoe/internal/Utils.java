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
package org.openhab.binding.entsoe.internal;

import java.time.Clock;
import java.time.ZoneId;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.TimeZoneProvider;

/**
 * The {@link Utils} Helpers for Solcast and ForecastSolar
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class Utils {
    private static TimeZoneProvider timeZoneProvider = new TimeZoneProvider() {
        @Override
        public ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }
    };

    private static Clock clock = Clock.systemDefaultZone();

    /**
     * Only for unit testing setting a fixed clock with desired date-time
     *
     * @param c
     */
    public static void setClock(Clock c) {
        clock = c;
    }

    public static void setTimeZoneProvider(TimeZoneProvider tzp) {
        timeZoneProvider = tzp;
    }

    public static Clock getClock() {
        return clock.withZone(timeZoneProvider.getTimeZone());
    }
}
