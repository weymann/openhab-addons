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

import static org.openhab.binding.eebus.internal.EEBusBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.handler.EEBusHandler;
import org.openhab.binding.eebus.internal.handler.EEBusNetworkHandler;
import org.openhab.binding.eebus.internal.handler.EEBusPeerHandler;
import org.openhab.binding.eebus.internal.transport.EEBusMetadataService;
import org.openhab.binding.eebus.internal.transport.EEBusPortPool;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link EEBusHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.eebus", service = ThingHandlerFactory.class)
public class EEBusHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_SERVICE, THING_TYPE_NETWORK,
            THING_TYPE_PEER);

    private final EEBusMetadataService metadataService;
    private final MDNSClient mdnsClient;
    private final EEBusPortPool portPool;

    @Activate
    public EEBusHandlerFactory(@Reference EEBusMetadataService metadataService, @Reference MDNSClient mdnsClient,
            @Reference EEBusPortPool portPool) {
        this.metadataService = metadataService;
        this.mdnsClient = mdnsClient;
        this.portPool = portPool;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_SERVICE.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            return new EEBusHandler(bridge, metadataService, mdnsClient, portPool);
        }
        if (THING_TYPE_NETWORK.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            return new EEBusNetworkHandler(bridge);
        }
        if (THING_TYPE_PEER.equals(thingTypeUID)) {
            return new EEBusPeerHandler(thing);
        }

        return null;
    }
}
