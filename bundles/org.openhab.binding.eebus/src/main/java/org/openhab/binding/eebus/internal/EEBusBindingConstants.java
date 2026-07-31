/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.eebus.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EEBusBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusBindingConstants {

    private static final String BINDING_ID = "eebus";

    // List of all Thing Type UIDs
    /** Bridge Thing: one local SHIP/SPINE service instance. */
    public static final ThingTypeUID THING_TYPE_SERVICE = new ThingTypeUID(BINDING_ID, "service");
    /** One paired remote EEBUS device, identified by its SKI. */
    public static final ThingTypeUID THING_TYPE_PEER = new ThingTypeUID(BINDING_ID, "peer");
}
