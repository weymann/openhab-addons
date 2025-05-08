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
package org.openhab.binding.curves.internal;

import static org.openhab.binding.curves.internal.CurvesConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.curves.internal.handler.BatteryHandler;
import org.openhab.binding.curves.internal.handler.GridHandler;
import org.openhab.binding.curves.internal.handler.HemsBridge;
import org.openhab.binding.curves.internal.handler.HouseholdHandler;
import org.openhab.binding.curves.internal.handler.PVHandler;
import org.openhab.binding.curves.internal.handler.WallboxHandler;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.persistence.PersistenceServiceRegistry;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CurvesHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.curves", service = ThingHandlerFactory.class)
public class CurvesHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(CurvesHandlerFactory.class);
    private ItemRegistry itemRegistry;
    private PersistenceServiceRegistry persistenceServiceRegistry;
    private TimeZoneProvider timeZoneProvider;

    @Activate
    public CurvesHandlerFactory(final @Reference ItemRegistry itemRegistry,
            final @Reference PersistenceServiceRegistry persistenceServiceRegistry,
            final @Reference TimeZoneProvider timeZoneProvider) {
        this.itemRegistry = itemRegistry;
        this.persistenceServiceRegistry = persistenceServiceRegistry;
        this.timeZoneProvider = timeZoneProvider;
        logger.info("[CURVES] Your configured timezone is {}", timeZoneProvider.getTimeZone().toString());
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_HEMS.equals(thingTypeUID)) {
            return new HemsBridge((Bridge) thing, itemRegistry, persistenceServiceRegistry, timeZoneProvider);
        } else if (THING_TYPE_PV.equals(thingTypeUID)) {
            return new PVHandler(thing);
        } else if (THING_TYPE_HOUSEHOLD.equals(thingTypeUID)) {
            return new HouseholdHandler(thing);
        } else if (THING_TYPE_BATTERY.equals(thingTypeUID)) {
            return new BatteryHandler(thing);
        } else if (THING_TYPE_GRID.equals(thingTypeUID)) {
            return new GridHandler(thing);
        } else if (THING_TYPE_WALLBOX.equals(thingTypeUID)) {
            return new WallboxHandler(thing);
        }

        return null;
    }
}
