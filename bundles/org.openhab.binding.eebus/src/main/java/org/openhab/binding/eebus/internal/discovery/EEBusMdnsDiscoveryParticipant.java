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

import static org.openhab.binding.eebus.internal.EEBusBindingConstants.PROPERTY_LOCAL_SKI;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.SERVICE_TYPE_SHIP_MDNS;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_NETWORK;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_PEER;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_SERVICE;

import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.config.EEBusPeerConfiguration;
import org.openhab.binding.eebus.internal.transport.EEBusShipTxtRecord;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Discovers real, unpaired {@code _ship._tcp.local.} (SHIP 7.3.2) EEBUS devices as
 * {@code eebus:peer} Inbox suggestions - independent of any {@code eebus:service}/
 * {@code eebus:network} Thing handler's lifecycle (see ADR-003).
 *
 * <p>
 * Registered as a plain OSGi service, this is picked up automatically by openHAB core's own
 * {@code MDNSDiscoveryService}, which owns scan scheduling and timeouts. Scanning therefore
 * starts as soon as the binding is installed, with no Bridge required to exist or be online -
 * this is what fixes "nothing happens after installing the binding" (ADR-003 Context).
 * </p>
 *
 * <p>
 * A result still needs an {@code eebus:network} Bridge Thing to attach to as its parent -
 * {@link #getThingUID(ServiceInfo)} and {@link #createResult(ServiceInfo)} look one up via
 * {@link ThingRegistry} and return {@code null} (no result) if none exists yet. Only the first
 * {@code eebus:network} Thing found is used - supporting more than one is out of scope for v1
 * (see ADR-003 "Negative consequences").
 * </p>
 *
 * <p>
 * {@link #createResult(ServiceInfo)} also excludes SKIs that already have an {@code eebus:peer}
 * Thing ({@link #isAlreadyKnown(String)} - CONCEPT.md §4.5: this Thing type no longer implies
 * trust/pairing, only that the device is already known) or belong to one of this openHAB
 * instance's own {@code eebus:service} Bridges ({@link #isOwnService(String)}) - without the
 * latter, a Bridge's own SHIP mDNS self-announcement (necessary so other EEBUS devices can find
 * it) would otherwise be re-discovered by this same participant and offered back as a bogus
 * Inbox suggestion for itself. See ADR-008.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@Component(service = MDNSDiscoveryParticipant.class)
public class EEBusMdnsDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final ThingRegistry thingRegistry;

    @Activate
    public EEBusMdnsDiscoveryParticipant(@Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Set.of(THING_TYPE_PEER);
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE_SHIP_MDNS;
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        EEBusShipTxtRecord txt = EEBusShipTxtRecord.from(service);
        if (txt == null) {
            return null;
        }
        ThingUID bridgeUid = findNetworkBridgeUid();
        if (bridgeUid == null || isAlreadyKnown(txt.ski()) || isOwnService(txt.ski())) {
            return null;
        }

        ThingUID thingUid = new ThingUID(THING_TYPE_PEER, bridgeUid, txt.ski());
        String label = txt.brand().isBlank() && txt.model().isBlank() ? service.getName()
                : (txt.brand() + " " + txt.model()).trim();

        return DiscoveryResultBuilder.create(thingUid).withBridge(bridgeUid).withProperty("ski", txt.ski())
                .withRepresentationProperty("ski").withLabel(label).build();
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        EEBusShipTxtRecord txt = EEBusShipTxtRecord.from(service);
        if (txt == null) {
            return null;
        }
        ThingUID bridgeUid = findNetworkBridgeUid();
        if (bridgeUid == null) {
            return null;
        }
        return new ThingUID(THING_TYPE_PEER, bridgeUid, txt.ski());
    }

    /**
     * @return the UID of the first {@code eebus:network} Bridge Thing found in the registry, or
     *         {@code null} if none exists yet.
     */
    private @Nullable ThingUID findNetworkBridgeUid() {
        return thingRegistry.getAll().stream().filter(thing -> THING_TYPE_NETWORK.equals(thing.getThingTypeUID()))
                .map(Thing::getUID).findFirst().orElse(null);
    }

    /**
     * @param ski the SKI read from a discovered service's TXT record
     * @return {@code true} if an {@code eebus:peer} Thing with this SKI already exists (i.e.
     *         already an Inbox-approved real-device record, not a pairing/trust statement -
     *         see CONCEPT.md §4.5), regardless of which {@code eebus:network} Bridge it
     *         belongs to
     */
    private boolean isAlreadyKnown(String ski) {
        return thingRegistry.getAll().stream().filter(thing -> THING_TYPE_PEER.equals(thing.getThingTypeUID()))
                .map(thing -> thing.getConfiguration().as(EEBusPeerConfiguration.class))
                .anyMatch(peerConfig -> ski.equals(peerConfig.ski));
    }

    /**
     * @param ski the SKI read from a discovered service's TXT record
     * @return {@code true} if this SKI belongs to one of this openHAB instance's own
     *         {@code eebus:service} Bridges (see {@link EEBusBindingConstants#PROPERTY_LOCAL_SKI},
     *         published once a Bridge's SHIP server is up). An {@code eebus:service} Bridge
     *         announces itself over mDNS so other EEBUS devices can find it - which means this
     *         same participant would otherwise re-discover it and offer it as a bogus
     *         {@code eebus:peer} Inbox suggestion for itself. See ADR-008.
     */
    private boolean isOwnService(String ski) {
        return thingRegistry.getAll().stream().filter(thing -> THING_TYPE_SERVICE.equals(thing.getThingTypeUID()))
                .anyMatch(thing -> ski.equals(thing.getProperties().get(PROPERTY_LOCAL_SKI)));
    }
}
