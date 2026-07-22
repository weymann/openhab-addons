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
package org.openhab.binding.boschthermotechnology.internal.discovery;

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_GATEWAY_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_ZONE_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.AC_STANDARD_FUNCTIONS;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.DHW_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.HEATING_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.POOL_ENABLED;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.PV_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.SOLAR_CIRCUITS_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.VENTILATION_OPERATION_MODE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.ResourcePaths.ZONES_LIST;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_AC_UNIT;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ENERGY_MONITORING;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_HEATPUMP;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_POOL;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_PV;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_VENTILATION_ZONE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.THING_TYPE_ZONE_THERMOSTAT;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.config.GatewayConfiguration;
import org.openhab.binding.boschthermotechnology.internal.handler.GatewayHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ChildThingDiscoveryService} probes one {@code gateway} bridge for the resources that
 * decide which of the eight ADR-005 child thing-types apply to it, and proposes one thing per
 * discovered capability. One instance is created and registered per {@code gateway} bridge by
 * {@code BoschThermotechnologyHandlerFactory}, mirroring how {@code GatewayDiscoveryService} is
 * registered per {@code account} bridge (ADR-004) - and, like that service, is also triggered
 * automatically once its bridge reaches {@code ONLINE} (see {@code GatewayHandler}).
 *
 * <p>
 * The discovery signal per child thing-type is documented in ADR-006:
 * {@code heatingCircuits}/{@code dhwCircuits} list reads for {@code heatpump} (and, alongside it,
 * {@code energy-monitoring}, which needs no probe of its own since it depends on the same heat
 * source existing); {@code pv/list}/{@code solarCircuits} for {@code pv}; existence probes
 * ({@code tryGetResource}, treating HTTP 404 as absence) for {@code pool}, {@code ventilation-zone}
 * and {@code ac-unit}; and the {@code zones/list} list read for one {@code zone-thermostat} thing
 * per entry. {@code water-softener} is intentionally not probed here yet - its resource path is
 * unconfirmed, see the thing-type description in {@code thing-types.xml} and the ADR-006 TODO.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ChildThingDiscoveryService extends AbstractDiscoveryService {

    private static final int TIMEOUT_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(ChildThingDiscoveryService.class);
    private final GatewayHandler gatewayHandler;

    public ChildThingDiscoveryService(GatewayHandler gatewayHandler) {
        super(Set.of(THING_TYPE_HEATPUMP, THING_TYPE_PV, THING_TYPE_POOL, THING_TYPE_VENTILATION_ZONE,
                THING_TYPE_ZONE_THERMOSTAT, THING_TYPE_ENERGY_MONITORING, THING_TYPE_AC_UNIT), TIMEOUT_SECONDS, false);
        this.gatewayHandler = gatewayHandler;
    }

    @Override
    protected void startScan() {
        ThingUID bridgeUID = gatewayHandler.getThing().getUID();
        String gatewayId = gatewayHandler.getThing().getConfiguration().as(GatewayConfiguration.class).gatewayId;
        if (gatewayId.isBlank()) {
            logger.debug("Skipping child thing discovery - gateway {} has no gatewayId configured yet", bridgeUID);
            return;
        }

        try {
            String accessToken = gatewayHandler.getValidAccessToken();
            PointTApiClient apiClient = gatewayHandler.getApiClient();

            boolean hasHeatpump = !apiClient.listResourceIds(accessToken, gatewayId, HEATING_CIRCUITS_LIST).isEmpty()
                    || !apiClient.listResourceIds(accessToken, gatewayId, DHW_CIRCUITS_LIST).isEmpty();
            if (hasHeatpump) {
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_HEATPUMP, "Heat Pump");
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_ENERGY_MONITORING, "Energy Monitoring");
            }

            boolean hasPv = !apiClient.listResourceIds(accessToken, gatewayId, PV_LIST).isEmpty()
                    || !apiClient.listResourceIds(accessToken, gatewayId, SOLAR_CIRCUITS_LIST).isEmpty();
            if (hasPv) {
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_PV, "Photovoltaic");
            }

            if (apiClient.tryGetResource(accessToken, gatewayId, POOL_ENABLED).isPresent()) {
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_POOL, "Pool");
            }

            // Discovered identically whether this gateway also has a heatpump child
            // (HeatPump-With-Ventilation-* SystemType) or is a standalone HRV unit (Ventilation
            // SystemType) - see ADR-006, "ventilation-zone placement - resolved".
            if (apiClient.tryGetResource(accessToken, gatewayId, VENTILATION_OPERATION_MODE).isPresent()) {
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_VENTILATION_ZONE, "Ventilation Zone");
            }

            if (apiClient.tryGetResource(accessToken, gatewayId, AC_STANDARD_FUNCTIONS).isPresent()) {
                proposeSingletonChild(bridgeUID, gatewayId, THING_TYPE_AC_UNIT, "Air Conditioner");
            }

            // water-softener: not probed - see class Javadoc and ADR-006.

            List<String> zoneIds = apiClient.listResourceIds(accessToken, gatewayId, ZONES_LIST);
            for (String zoneId : zoneIds) {
                proposeZoneThermostat(bridgeUID, gatewayId, zoneId);
            }
        } catch (PointTApiException e) {
            logger.debug("Child thing discovery failed for gateway {}: {}", gatewayId, e.getMessage());
        }
    }

    private void proposeSingletonChild(ThingUID bridgeUID, String gatewayId, ThingTypeUID thingTypeUID, String label) {
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingTypeUID.getId());
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                .withProperty(CONFIG_GATEWAY_ID, gatewayId).withRepresentationProperty(CONFIG_GATEWAY_ID)
                .withLabel("Bosch Thermotechnology " + label).build();
        thingDiscovered(result);
    }

    private void proposeZoneThermostat(ThingUID bridgeUID, String gatewayId, String zoneId) {
        ThingUID thingUID = new ThingUID(THING_TYPE_ZONE_THERMOSTAT, bridgeUID, "zone" + zoneId);
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                .withProperty(CONFIG_GATEWAY_ID, gatewayId).withProperty(CONFIG_ZONE_ID, zoneId)
                .withRepresentationProperty(CONFIG_ZONE_ID)
                .withLabel("Bosch Thermotechnology Zone Thermostat " + zoneId).build();
        thingDiscovered(result);
    }
}
