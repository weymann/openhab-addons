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
package org.openhab.binding.boschthermotechnology.internal.handler;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_AC_UNIT;

import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;

/**
 * @author Bernd Weymann - Initial contribution
 */
class AcUnitHandlerTest extends AbstractSimpleChildHandlerTest {

    @Override
    protected ThingTypeUID thingType() {
        return THING_TYPE_AC_UNIT;
    }

    @Override
    protected BaseThingHandler createHandler(Thing thing) {
        return new AcUnitHandler(thing);
    }
}
