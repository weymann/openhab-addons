/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Miika Jukka - Initial contribution
 *
 */
@NonNullByDefault
public class ChannelMapper {

    private static final Map<Integer, String> CHANNEL_MAP = Map.ofEntries(Map.entry(0, "priceNow"),
            Map.entry(1, "priceHours01"), Map.entry(2, "priceHours02"), Map.entry(3, "priceHours03"),
            Map.entry(4, "priceHours04"), Map.entry(5, "priceHours05"), Map.entry(6, "priceHours06"),
            Map.entry(7, "priceHours07"), Map.entry(8, "priceHours08"), Map.entry(9, "priceHours09"),
            Map.entry(10, "priceHours10"), Map.entry(11, "priceHours11"), Map.entry(12, "priceHours12"),
            Map.entry(13, "priceHours13"), Map.entry(14, "priceHours14"), Map.entry(15, "priceHours15"),
            Map.entry(16, "priceHours16"), Map.entry(17, "priceHours17"), Map.entry(18, "priceHours18"),
            Map.entry(19, "priceHours19"), Map.entry(20, "priceHours20"), Map.entry(21, "priceHours21"),
            Map.entry(22, "priceHours22"), Map.entry(23, "priceHours23"), Map.entry(24, "priceHours24"));

    public static @Nullable String getChannelID(Integer hour) {
        return CHANNEL_MAP.get(hour);
    }

    public static Integer getHour(String channelID) {
        return CHANNEL_MAP.entrySet().stream().filter(k -> k.getValue().equals(channelID)).map(Map.Entry::getKey)
                .findFirst().orElse(99);
    }
}
