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
package org.openhab.binding.eebus.internal.discovery;

import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_PEER;

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.eebus.internal.handler.EEBusHandler;
import org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Bridge-scoped discovery service (CONCEPT.md §4.1, §7 item 10): turns
 * {@link EEBusMdnsBrowser} sightings of {@code _ship._tcp.local.} services into Inbox entries
 * for {@code eebus:peer} Things with a friendly name (requirement 3, "Discovery mit Namen"),
 * excluding services whose SKI already belongs to a paired peer Thing.
 *
 * <p>
 * Implemented per the verified openHAB binding developer docs pattern "Discovery that is bound
 * to a Bridge" (see CONCEPT.md Quellen): a {@code PROTOTYPE}-scoped
 * {@link AbstractThingHandlerDiscoveryService}, activated via {@link EEBusHandler#getServices()}.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(scope = ServiceScope.PROTOTYPE, service = EEBusDiscoveryService.class)
public class EEBusDiscoveryService extends AbstractThingHandlerDiscoveryService<EEBusHandler>
        implements EEBusMdnsBrowser.Listener {

    private static final int TIMEOUT_SECONDS = 10;

    public EEBusDiscoveryService() {
        super(EEBusHandler.class, Set.of(THING_TYPE_PEER), TIMEOUT_SECONDS, true);
    }

    @Override
    public void initialize() {
        EEBusMdnsBrowser browser = thingHandler.getMdnsBrowser();
        if (browser != null) {
            browser.addListener(this);
        }
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        EEBusMdnsBrowser browser = thingHandler.getMdnsBrowser();
        if (browser != null) {
            browser.removeListener(this);
        }
    }

    @Override
    protected void startScan() {
        EEBusMdnsBrowser browser = thingHandler.getMdnsBrowser();
        if (browser == null) {
            return;
        }
        onDiscoveredServicesChanged(browser.getDiscoveredServices());
    }

    @Override
    public void onDiscoveredServicesChanged(Collection<EEBusMdnsBrowser.DiscoveredService> services) {
        Set<String> pairedSkis = thingHandler.pairedSkis();
        ThingUID bridgeUid = thingHandler.getThing().getUID();
        for (EEBusMdnsBrowser.DiscoveredService service : services) {
            if (pairedSkis.contains(service.ski())) {
                continue;
            }
            ThingUID thingUid = new ThingUID(THING_TYPE_PEER, bridgeUid, service.ski());
            String label = service.brand().isBlank() && service.model().isBlank() ? service.name()
                    : (service.brand() + " " + service.model()).trim();
            DiscoveryResult result = DiscoveryResultBuilder.create(thingUid).withBridge(bridgeUid)
                    .withProperty("ski", service.ski()).withRepresentationProperty("ski").withLabel(label).build();
            thingDiscovered(result);
        }
    }
}
