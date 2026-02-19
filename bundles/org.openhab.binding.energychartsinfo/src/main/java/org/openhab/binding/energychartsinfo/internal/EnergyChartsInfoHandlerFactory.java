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
package org.openhab.binding.energychartsinfo.internal;

import static org.openhab.binding.energychartsinfo.internal.EnergyChartsInfoBindingConstants.THING_TYPE_ENERGY_CHARTS_INFO;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link EnergyChartsInfoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.energychartsinfo", service = ThingHandlerFactory.class)
public class EnergyChartsInfoHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ENERGY_CHARTS_INFO);

    private final HttpClientFactory hcf;
    private final TimeZoneProvider tzp;

    @Activate
    public EnergyChartsInfoHandlerFactory(@Reference HttpClientFactory hcf, final @Reference TimeZoneProvider tzp) {
        this.hcf = hcf;
        this.tzp = tzp;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ENERGY_CHARTS_INFO.equals(thingTypeUID)) {
            return new EnergyChartsInfoHandler(thing, hcf.getCommonHttpClient(), tzp);
        }

        return null;
    }
}
