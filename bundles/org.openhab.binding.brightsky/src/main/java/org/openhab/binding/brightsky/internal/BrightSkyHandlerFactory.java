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
package org.openhab.binding.brightsky.internal;

import static org.openhab.binding.brightsky.internal.BrightSkyBindingConstants.THING_TYPE_WEATHER_LOCATION;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.brightsky.internal.handler.BrightSkyHandler;
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
 * OSGi {@link ThingHandlerFactory} that creates {@link BrightSkyHandler} instances.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.brightsky", service = ThingHandlerFactory.class)
public class BrightSkyHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_WEATHER_LOCATION);

    private final HttpClientFactory httpClientFactory;

    /**
     * Activates the factory with injected OSGi services.
     *
     * @param httpClientFactory framework-provided HTTP client factory
     */
    @Activate
    public BrightSkyHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_WEATHER_LOCATION.equals(thing.getThingTypeUID())) {
            return new BrightSkyHandler(thing, httpClientFactory);
        }
        return null;
    }
}
