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
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_HW_DEVICE;
import static org.openhab.binding.eebus.internal.EEBusBindingConstants.THING_TYPE_OH_DEVICE;

import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.config.EEBusHwDeviceConfiguration;
import org.openhab.binding.eebus.internal.transport.EEBusShipTxtRecord;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Discovers real, untrusted {@code _ship._tcp.local.} (SHIP 7.3.2) EEBUS devices as
 * {@code eebus:hw-device} Inbox suggestions - independent of any {@code eebus:oh-device} Thing
 * handler's lifecycle (see ADR-003).
 *
 * <p>
 * Registered as a plain OSGi service, this is picked up automatically by openHAB core's own
 * {@code MDNSDiscoveryService}, which owns scan scheduling and timeouts. Scanning therefore
 * starts as soon as the binding is installed, with no Bridge required to exist or be online -
 * this is what fixes "nothing happens after installing the binding" (ADR-003 Context).
 * </p>
 *
 * <p>
 * <strong>Revised (docs/ADR/020-merge-network-peer-things.md):</strong> a result no longer needs
 * a Bridge to attach to - the former {@code eebus:network} anchor Bridge added nothing beyond
 * being a mandatory, always-ONLINE parent, so {@link #getThingUID(ServiceInfo)} and
 * {@link #createResult(ServiceInfo)} now build a top-level {@code eebus:hw-device} Thing UID
 * directly from the discovered SKI, unconditionally (still subject to the exclusions below).
 * This also retires ADR-004 (a Bridge-add retry-scan listener), whose entire premise - a
 * mandatory Bridge that must exist before results can be produced - no longer applies.
 * </p>
 *
 * <p>
 * {@link #createResult(ServiceInfo)} also excludes SKIs that already have an
 * {@code eebus:hw-device} Thing ({@link #isAlreadyKnown(String)} - CONCEPT.md §4.5: this
 * Thing type no longer implies trust, only that the device is already known) or belong
 * to one of this openHAB instance's own {@code eebus:oh-device} Bridges
 * ({@link #isOwnService(String)}) - without the latter, a Bridge's own SHIP mDNS
 * self-announcement (necessary so other EEBUS devices can find it) would otherwise be
 * re-discovered by this same participant and offered back as a bogus Inbox suggestion for
 * itself. See ADR-008.
 * </p>
 *
 * <p>
 * <strong>Renamed (docs/ADR/024-oh-device-oh-entity-rename.md):</strong> this class produced
 * {@code eebus:eebus-peer} results before this ADR; the class itself keeps its original name.
 * While touching this rename, {@link #isOwnService(String)} was found to only ever have checked
 * {@code eebus:service}/{@code eebus:oh-device} Bridges' own SKI, never
 * {@code eebus:cs-service}/{@code eebus:oh-cs-device} - a pre-existing gap unrelated to the
 * rename itself, meaning a {@code cs-service} Bridge's own mDNS self-announcement was not
 * excluded from Inbox suggestions the way an {@code eebus:service} Bridge's already was. Fixed
 * at the time to check both Bridge types; moot now that {@code eebus:oh-cs-device} itself is
 * removed (docs/ADR/027-derive-local-use-cases-from-entities.md) - {@link #isOwnService(String)}
 * checks only {@code eebus:oh-device} again.
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
        return Set.of(THING_TYPE_HW_DEVICE);
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
        if (isAlreadyKnown(txt.ski()) || isOwnService(txt.ski())) {
            return null;
        }

        ThingUID thingUid = new ThingUID(THING_TYPE_HW_DEVICE, txt.ski());
        String label = txt.brand().isBlank() && txt.model().isBlank() ? service.getName()
                : (txt.brand() + " " + txt.model()).trim();

        return DiscoveryResultBuilder.create(thingUid).withProperty("ski", txt.ski()).withRepresentationProperty("ski")
                .withLabel(label).build();
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        EEBusShipTxtRecord txt = EEBusShipTxtRecord.from(service);
        if (txt == null) {
            return null;
        }
        return new ThingUID(THING_TYPE_HW_DEVICE, txt.ski());
    }

    /**
     * @param ski the SKI read from a discovered service's TXT record
     * @return {@code true} if an {@code eebus:hw-device} Thing with this SKI already exists
     *         (i.e. already an Inbox-approved real-device record, not a trust statement - see
     *         CONCEPT.md §4.5)
     */
    private boolean isAlreadyKnown(String ski) {
        return thingRegistry.getAll().stream().filter(thing -> THING_TYPE_HW_DEVICE.equals(thing.getThingTypeUID()))
                .map(thing -> thing.getConfiguration().as(EEBusHwDeviceConfiguration.class))
                .anyMatch(hwDeviceConfig -> ski.equals(hwDeviceConfig.ski));
    }

    /**
     * @param ski the SKI read from a discovered service's TXT record
     * @return {@code true} if this SKI belongs to one of this openHAB instance's own
     *         {@code eebus:oh-device} Bridges (see
     *         {@link org.openhab.binding.eebus.internal.EEBusBindingConstants#PROPERTY_LOCAL_SKI},
     *         published once a Bridge's SHIP server is up). Such a Bridge announces itself over mDNS so other EEBUS
     *         devices can
     *         find it - which means this same participant would otherwise re-discover it and
     *         offer it as a bogus {@code eebus:hw-device} Inbox suggestion for itself. See
     *         ADR-008. {@code eebus:oh-device} is the only Bridge Thing type now
     *         (docs/ADR/027-derive-local-use-cases-from-entities.md removed the former
     *         {@code eebus:oh-cs-device} this used to also check - see class javadoc "Renamed").
     */
    private boolean isOwnService(String ski) {
        return thingRegistry.getAll().stream().filter(thing -> THING_TYPE_OH_DEVICE.equals(thing.getThingTypeUID()))
                .anyMatch(thing -> ski.equals(thing.getProperties().get(PROPERTY_LOCAL_SKI)));
    }
}
