/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.dirigera.internal.discovery;

import static org.openhab.binding.dirigera.internal.Constants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DirigeraDiscoveryService} will be notified from Bridge {@link AccountHandler} regarding
 * associated vehicles and provides DiscoveryResults
 *
 * @author Bernd Weymann - Initial Contribution
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.dirigera")
public class DirigeraDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(DirigeraDiscoveryService.class);

    private DirigeraDiscoveryManager dirigeraDiscoveryManager;

    @Activate
    public DirigeraDiscoveryService(final @Reference DirigeraDiscoveryManager manager) {
        super(SUPPORTED_THING_TYPES_UIDS, 90, true);
        dirigeraDiscoveryManager = manager;
        dirigeraDiscoveryManager.setDiscoverService(this);
    }

    public synchronized void gatewayDiscovered(String ip, Map<String, Object> properties) {
        DiscoveryResult discoveryResult = DiscoveryResultBuilder
                .create(new ThingUID(THING_TYPE_GATEWAY, ip.replace(".", "-"))).withLabel("DIRIGERA Gateway " + ip)
                .withRepresentationProperty(PROPERTY_IP_ADDRESS).withProperties(properties).build();
        thingDiscovered(discoveryResult);
    }

    public void deviceDiscovered(DiscoveryResult result) {
        thingDiscovered(result);
    }

    public void deviceRemoved(DiscoveryResult result) {
        thingRemoved(result.getThingUID());
    }

    @Override
    protected void startScan() {
        dirigeraDiscoveryManager.scanForHub();
    }
}
