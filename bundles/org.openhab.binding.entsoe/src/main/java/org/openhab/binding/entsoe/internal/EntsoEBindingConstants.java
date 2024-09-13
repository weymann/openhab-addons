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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EntsoEBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jørgen Melhus - Initial contribution
 * @author Miika Jukka - Initial contribution
 */
@NonNullByDefault
public class EntsoEBindingConstants {

    public static final String BINDING_ID = "entsoe";
    public static final ThingTypeUID THING_TYPE_SERVICE = new ThingTypeUID(BINDING_ID, "service");
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPE_UIDS = Set.of(THING_TYPE_SERVICE);

    public static final String CHANNEL_GROUP_ELECTRICITY = "electricity";
    public static final String CHANNEL_SPOT_PRICE = "spot-price";
    public static final String CHANNEL_UPDATED = "updated";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_EVENT_DAY_AHEAD = "DAY_AHEAD_AVAILABLE";
    public static final int API_TIMEOUT = 30000;
}
