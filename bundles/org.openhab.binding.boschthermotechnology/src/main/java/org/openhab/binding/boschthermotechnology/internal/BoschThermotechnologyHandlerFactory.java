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
package org.openhab.binding.boschthermotechnology.internal;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.*;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.SingleKeyIdAuthClient;
import org.openhab.binding.boschthermotechnology.internal.discovery.ChildThingDiscoveryService;
import org.openhab.binding.boschthermotechnology.internal.discovery.GatewayDiscoveryService;
import org.openhab.binding.boschthermotechnology.internal.handler.AcUnitHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.AccountBridgeHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.EnergyMonitoringHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.GatewayHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.HeatpumpHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.PoolHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.PvHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.VentilationZoneHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.WaterSoftenerHandler;
import org.openhab.binding.boschthermotechnology.internal.handler.ZoneThermostatHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.Gson;

/**
 * The {@link BoschThermotechnologyHandlerFactory} creates every handler this binding defines and
 * registers/unregisters their discovery services: {@link AccountBridgeHandler} plus a
 * {@link GatewayDiscoveryService} per account bridge (unchanged, ADR-004), and - since
 * ADR-005/ADR-006 - {@link GatewayHandler} plus a {@link ChildThingDiscoveryService} per gateway
 * bridge, and the eight plain child handlers ({@link HeatpumpHandler}, {@link PvHandler},
 * {@link PoolHandler}, {@link VentilationZoneHandler}, {@link ZoneThermostatHandler},
 * {@link EnergyMonitoringHandler}, {@link AcUnitHandler}, {@link WaterSoftenerHandler}).
 *
 * <p>
 * TODO ($Dev): this uses constructor-based {@code @Reference}/{@code @Activate} injection, which
 * requires a recent enough bnd/SCR annotation processor version - verify this compiles against
 * this bundle's actual {@code pom.xml}-inherited parent version; fall back to a field-based
 * {@code @Reference} + setter if it does not.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.boschthermotechnology", service = ThingHandlerFactory.class)
public class BoschThermotechnologyHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_GATEWAY,
            THING_TYPE_HEATPUMP, THING_TYPE_PV, THING_TYPE_POOL, THING_TYPE_VENTILATION_ZONE,
            THING_TYPE_ZONE_THERMOSTAT, THING_TYPE_ENERGY_MONITORING, THING_TYPE_AC_UNIT, THING_TYPE_WATER_SOFTENER);

    private final HttpClientFactory httpClientFactory;
    private final StorageService storageService;

    /**
     * Shared by both discovery-service kinds ({@code GatewayDiscoveryService} per account bridge,
     * {@code ChildThingDiscoveryService} per gateway bridge) - safe because a {@link ThingUID}
     * already encodes the thing type, so account and gateway bridge UIDs never collide as keys.
     */
    private final Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegistrations = new ConcurrentHashMap<>();

    @Activate
    public BoschThermotechnologyHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference StorageService storageService) {
        this.httpClientFactory = httpClientFactory;
        this.storageService = storageService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            Gson gson = new Gson();
            HttpClient httpClient = httpClientFactory.getCommonHttpClient();
            SingleKeyIdAuthClient authClient = new SingleKeyIdAuthClient(httpClient, gson);
            PointTApiClient apiClient = new PointTApiClient(httpClient, gson);
            // One Storage per bridge instance, keyed by its own UID - keeps tokens of different
            // accounts separate without needing to prefix keys manually.
            Storage<String> tokenStorage = storageService.getStorage(bridge.getUID().getAsString());
            AccountBridgeHandler bridgeHandler = new AccountBridgeHandler(bridge, authClient, apiClient, tokenStorage);
            GatewayDiscoveryService discoveryService = registerGatewayDiscoveryService(bridgeHandler);
            // In addition to the user manually triggering a scan from the Inbox, also scan once
            // automatically every time the bridge reaches ONLINE (fresh login or a restored
            // session), so newly registered gateways show up without a manual step.
            bridgeHandler.setDiscoveryScanTrigger(() -> discoveryService.startScan(null));
            return bridgeHandler;
        }

        if (THING_TYPE_GATEWAY.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            GatewayHandler gatewayHandler = new GatewayHandler(bridge);
            ChildThingDiscoveryService discoveryService = registerChildThingDiscoveryService(gatewayHandler);
            // Mirrors the account bridge's own automatic scan-on-online behavior (ADR-004) one
            // level down - see GatewayHandler.updateStatus(...).
            gatewayHandler.setChildDiscoveryScanTrigger(() -> discoveryService.startScan(null));
            return gatewayHandler;
        }

        if (THING_TYPE_HEATPUMP.equals(thingTypeUID)) {
            return new HeatpumpHandler(thing);
        }
        if (THING_TYPE_PV.equals(thingTypeUID)) {
            return new PvHandler(thing);
        }
        if (THING_TYPE_POOL.equals(thingTypeUID)) {
            return new PoolHandler(thing);
        }
        if (THING_TYPE_VENTILATION_ZONE.equals(thingTypeUID)) {
            return new VentilationZoneHandler(thing);
        }
        if (THING_TYPE_ZONE_THERMOSTAT.equals(thingTypeUID)) {
            return new ZoneThermostatHandler(thing);
        }
        if (THING_TYPE_ENERGY_MONITORING.equals(thingTypeUID)) {
            return new EnergyMonitoringHandler(thing);
        }
        if (THING_TYPE_AC_UNIT.equals(thingTypeUID)) {
            return new AcUnitHandler(thing);
        }
        if (THING_TYPE_WATER_SOFTENER.equals(thingTypeUID)) {
            return new WaterSoftenerHandler(thing);
        }

        return null;
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof AccountBridgeHandler || thingHandler instanceof GatewayHandler) {
            ServiceRegistration<?> registration = discoveryServiceRegistrations
                    .remove(thingHandler.getThing().getUID());
            if (registration != null) {
                registration.unregister();
            }
        }
        super.removeHandler(thingHandler);
    }

    private GatewayDiscoveryService registerGatewayDiscoveryService(AccountBridgeHandler bridgeHandler) {
        GatewayDiscoveryService discoveryService = new GatewayDiscoveryService(bridgeHandler);
        ServiceRegistration<?> registration = bundleContext.registerService(DiscoveryService.class.getName(),
                discoveryService, new Hashtable<>());
        discoveryServiceRegistrations.put(bridgeHandler.getThing().getUID(), registration);
        return discoveryService;
    }

    private ChildThingDiscoveryService registerChildThingDiscoveryService(GatewayHandler gatewayHandler) {
        ChildThingDiscoveryService discoveryService = new ChildThingDiscoveryService(gatewayHandler);
        ServiceRegistration<?> registration = bundleContext.registerService(DiscoveryService.class.getName(),
                discoveryService, new Hashtable<>());
        discoveryServiceRegistrations.put(gatewayHandler.getThing().getUID(), registration);
        return discoveryService;
    }
}
