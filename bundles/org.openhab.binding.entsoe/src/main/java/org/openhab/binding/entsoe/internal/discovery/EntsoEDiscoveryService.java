/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.entsoe.internal.discovery;

import static org.openhab.binding.entsoe.internal.EntsoEBindingConstants.THING_TYPE_DAY_AHEAD_PRICES;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.entsoe.internal.AreaCode;
import org.openhab.binding.entsoe.internal.exception.EntsoEConfigurationException;
import org.openhab.binding.entsoe.internal.exception.EntsoEResponseException;
import org.openhab.binding.entsoe.internal.exception.EntsoEUnexpectedException;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.i18n.LocationProvider;
import org.openhab.core.library.types.PointType;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EntsoEDiscoveryService} creates things based on location.
 *
 * @author Miika Jukka - Initial contribution
 *
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.entsoe")
public class EntsoEDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(EntsoEDiscoveryService.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPE_UIDS = Set.of(THING_TYPE_DAY_AHEAD_PRICES);
    private static final int DISCOVERY_TIMEOUT_SECONDS = 5;

    private @NonNullByDefault({}) LocationProvider locationProvider;

    public EntsoEDiscoveryService() {
        super(SUPPORTED_THING_TYPE_UIDS, DISCOVERY_TIMEOUT_SECONDS, true);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting EntsoE discovery");
        AreaCode areaCode = null;
        PointType location = null;
        location = locationProvider.getLocation();
        if (location == null) {
            logger.debug("Location not set. Aborting discovery");
            return;
        }
        String lonParam = location.getLatitude().toString();
        String latParam = location.getLongitude().toString();

        OpenStreetMapRequest request = new OpenStreetMapRequest(lonParam, latParam);
        OpenStreetMapClient client = new OpenStreetMapClient();

        try {
            areaCode = client.doGetRequest(request);
            logger.debug("AreaCode: {}", areaCode.toString());
            ThingUID thingUID = new ThingUID(THING_TYPE_DAY_AHEAD_PRICES, areaCode.country.toLowerCase());

            //@formatter:off
            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                    .withLabel(String.format("EntsoE spot prices for %s", areaCode.country))
                    .withProperty("area", areaCode.areaCode)
                    .withRepresentationProperty("area")
                    .build();
            thingDiscovered(discoveryResult);
            //@formatter:on

        } catch (EntsoEResponseException | EntsoEUnexpectedException | EntsoEConfigurationException e) {
            logger.debug("Discovery failed: ", e);
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Start EntsoE background discovery");
        startScan();
    }

    @Override
    protected void stopBackgroundDiscovery() {
    }

    @Reference
    protected void setLocationProvider(LocationProvider locationProvider) {
        this.locationProvider = locationProvider;
    }

    protected void unsetLocationProvider(LocationProvider provider) {
        this.locationProvider = null;
    }

    protected LocationProvider getLocationProvider() {
        return locationProvider;
    }
}
