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
import org.openhab.binding.eebus.internal.handler.EEBusHwDeviceHandler;
import org.openhab.binding.eebus.internal.handler.EEBusOhEntityHandler;
import org.openhab.binding.eebus.internal.transport.EEBusMetadataService;
import org.openhab.binding.eebus.internal.transport.EEBusPortPool;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.storage.StorageService;
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

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_OH_DEVICE,
            THING_TYPE_HW_DEVICE, THING_TYPE_OH_ENTITY, THING_TYPE_OH_CS_ENTITY, THING_TYPE_OH_EG_ENTITY,
            THING_TYPE_OH_MPC_ENTITY, THING_TYPE_OH_HEMS_ENTITY);

    private final EEBusMetadataService metadataService;
    private final MDNSClient mdnsClient;
    private final EEBusPortPool portPool;
    private final StorageService storageService;
    private final NetworkAddressService networkAddressService;

    @Activate
    public EEBusHandlerFactory(@Reference EEBusMetadataService metadataService, @Reference MDNSClient mdnsClient,
            @Reference EEBusPortPool portPool, @Reference StorageService storageService,
            @Reference NetworkAddressService networkAddressService) {
        this.metadataService = metadataService;
        this.mdnsClient = mdnsClient;
        this.portPool = portPool;
        this.storageService = storageService;
        this.networkAddressService = networkAddressService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        // eebus:oh-cs-device (docs/ADR/023-cs-service-convenience-bridge.md) used to be handled
        // here too, as a second Bridge Thing type served by the same EEBusHandler class. Removed
        // by docs/ADR/027-derive-local-use-cases-from-entities.md: eebus:oh-device is now the
        // only Bridge Thing type, and EEBusHandler derives its local Use-Case set from its
        // attached Entity children instead of branching on which Bridge Thing type it is.
        if (THING_TYPE_OH_DEVICE.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            return new EEBusHandler(bridge, metadataService, mdnsClient, portPool, storageService,
                    networkAddressService);
        }
        if (THING_TYPE_HW_DEVICE.equals(thingTypeUID)) {
            return new EEBusHwDeviceHandler(thing);
        }
        if (THING_TYPE_OH_ENTITY.equals(thingTypeUID)) {
            return new EEBusOhEntityHandler(thing);
        }
        // eebus:oh-cs-entity (docs/ADR/025-oh-cs-entity-static-channels.md) is served by the
        // very same EEBusOhEntityHandler class as eebus:oh-entity - its behavior (trust-status
        // derivation, applyLimitStatus/applyFailsafeStatus) is identical; only its thing-types.xml
        // declaration differs (exclusive to eebus:oh-cs-device, statically declared lpc/lpp
        // Channel Groups instead of dynamically created ones).
        if (THING_TYPE_OH_CS_ENTITY.equals(thingTypeUID)) {
            return new EEBusOhEntityHandler(thing);
        }
        // eebus:oh-eg-entity (docs/ADR/026-oh-eg-entity-static-channels.md) is served by the
        // very same EEBusOhEntityHandler class as eebus:oh-entity/eebus:oh-cs-entity - only its
        // thing-types.xml declaration differs (statically declared lpc/lpp Channel Groups,
        // intended for use under eebus:oh-device when its LPC/LPP Client-role Use Cases are
        // configured; no dedicated convenience Bridge, unlike eebus:oh-cs-entity).
        if (THING_TYPE_OH_EG_ENTITY.equals(thingTypeUID)) {
            return new EEBusOhEntityHandler(thing);
        }
        // eebus:oh-mpc-entity (docs/ADR/036-oh-mpc-entity-static-channels.md) is served by the
        // very same EEBusOhEntityHandler class as the other three Entity Thing types - only its
        // thing-types.xml declaration differs (statically declared mpc Channel Group, dedicated
        // to the MPC Client role; no dedicated convenience Bridge, same non-exclusive pattern as
        // eebus:oh-eg-entity).
        if (THING_TYPE_OH_MPC_ENTITY.equals(thingTypeUID)) {
            return new EEBusOhEntityHandler(thing);
        }
        // eebus:oh-hems-entity (docs/ADR/053-hems-convenience-entity.md) is served by the very
        // same EEBusOhEntityHandler class as the other Entity Thing types.
        if (THING_TYPE_OH_HEMS_ENTITY.equals(thingTypeUID)) {
            return new EEBusOhEntityHandler(thing);
        }

        return null;
    }
}
