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
package org.openhab.binding.eebus.internal.transport;

import static org.openhab.binding.eebus.internal.EEBusBindingConstants.SERVICE_TYPE_SHIP_MDNS;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Independent mDNS browser for {@code _ship._tcp.local.} services (SHIP 7.3.2 TXT record).
 *
 * <p>
 * <strong>Why this exists instead of using jeebus.ship/jeebus.spine directly</strong> (see
 * CONCEPT.md §4.1, §5.4 TODO 2, §7 item 9):
 * </p>
 * <ul>
 * <li>jeebus.ship's own mDNS classes ({@code org.openmuc.jeebus.ship.node.service.ServiceRegistry},
 * {@code TxtRecord}) live in a package that is <strong>not</strong> in the ship OSGi bundle's
 * {@code Export-Package} (verified against {@code ship/build.gradle}) - not usable as a
 * dependency.</li>
 * <li>{@code ShipCommunication} (jeebus.spine) builds its own internal {@code ConnectionHandler}
 * and does not expose raw {@code serviceAdded}/mDNS events externally.</li>
 * </ul>
 *
 * <p>
 * This class re-implements the (public, standard) discovery step on top of openHAB core's
 * shared {@link MDNSClient} service (rather than creating its own {@code JmDNS} instance via
 * {@code JmDNS.create()}) - this reuses the JmDNS instance(s) openHAB core already runs for
 * every binding on the system instead of spinning up a second, independent mDNS responder, and
 * sidesteps any {@code javax.jmdns} version conflict between this binding's own dependency and
 * whatever {@code org.openhab.core.io.transport.mdns} already provides at runtime (see the
 * {@code pom.xml} comment on the {@code org.jmdns:jmdns} dependency).
 * </p>
 *
 * <p>
 * <strong>Revised (ADR-003):</strong> this class's Thing-discovery-inbox responsibility
 * (requirement 3, "Discovery mit Namen") has moved to the binding-scoped
 * {@code EEBusMdnsDiscoveryParticipant}, which needs no active {@code eebus:service} session
 * and starts scanning as soon as the binding is installed. This class keeps its other,
 * genuinely session-bound purpose: maintaining a {@code communicationAddress -> SKI} map for an
 * <em>active</em> {@code eebus:service} identity, so that
 * {@code UseCasePartner#getCommunicationAddress()} can be resolved back to a paired
 * {@code eebus:peer} Thing. Both classes register their own listener against the same shared
 * {@link MDNSClient}/JmDNS instance(s) - this is <strong>not</strong> redundant: no second
 * JmDNS responder or duplicate network traffic is created, only two independent listener
 * registrations for the same events, each serving a different, non-overlapping purpose.
 * </p>
 *
 * <p>
 * <strong>Verified string format</strong>: {@code communicationAddress} is
 * {@code "<ipv4>:<port>"} (or {@code "[<ipv6>]:<port>"} if no IPv4 address is present) - this
 * was confirmed against the actual {@code org.openmuc.jeebus:ship:2.2.0} source
 * ({@code ServiceRegistry#getIpAndPort}, github.com/openmuc/jeebus.ship, tag {@code v2.2.0}),
 * which is what {@code ConnectionHandler#serviceAdded(String ipAddr, String ski)} /
 * {@code #connectionDataExchangeEnabled(String ipAddr)} are called with, and in turn what
 * {@code Communication#addDevice(String communicationAddress)} passes on to SPINE. This class
 * builds the exact same string format so lookups actually match.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusMdnsBrowser implements ServiceListener, AutoCloseable {

    /** One discovered remote EEBUS/SHIP service, read straight from its TXT record. */
    public record DiscoveredService(String ski, String communicationAddress, String name, String brand, String model,
            String type) {
    }

    private final Logger logger = LoggerFactory.getLogger(EEBusMdnsBrowser.class);

    private final MDNSClient mdnsClient;
    private final Map<String, DiscoveredService> byCommunicationAddress = new ConcurrentHashMap<>();
    private final Map<String, DiscoveredService> bySki = new ConcurrentHashMap<>();
    /** mDNS event name -> SKI, needed to resolve serviceRemoved() events (no TXT record there). */
    private final Map<String, String> nameToSki = new ConcurrentHashMap<>();

    /**
     * @param mdnsClient openHAB core's shared mDNS service (injected, see
     *            {@code org.openhab.binding.eebus.internal.handler.EEBusHandlerFactory}/
     *            {@code org.openhab.binding.eebus.internal.handler.EEBusHandler}) - no longer
     *            creates its own {@code JmDNS} instance.
     */
    public EEBusMdnsBrowser(MDNSClient mdnsClient) {
        this.mdnsClient = mdnsClient;
        mdnsClient.addServiceListener(SERVICE_TYPE_SHIP_MDNS, this);
    }

    /** @return all currently visible EEBUS/SHIP services, keyed implicitly by SKI. */
    public Collection<DiscoveredService> getDiscoveredServices() {
        return List.copyOf(bySki.values());
    }

    /**
     * Resolves a SPINE {@code communicationAddress} (as returned by e.g.
     * {@code UseCasePartner#getCommunicationAddress()}) back to the SKI of the remote service,
     * if currently known.
     */
    public Optional<String> skiForCommunicationAddress(String communicationAddress) {
        return Optional.ofNullable(byCommunicationAddress.get(communicationAddress)).map(DiscoveredService::ski);
    }

    // javax.jmdns.ServiceListener is an unannotated legacy interface - @NonNullByDefault({})
    // opts these three overrides out of this class's default non-null constraint on the
    // ServiceEvent parameter, matching the interface's unconstrained signature (otherwise ecj
    // reports "Illegal redefinition of parameter event").
    @Override
    @NonNullByDefault({})
    public void serviceAdded(ServiceEvent event) {
        // Force serviceResolved() to be called (mirrors jeebus.ship's own ServiceRegistry).
        event.getDNS().requestServiceInfo(event.getType(), event.getName());
    }

    @Override
    @NonNullByDefault({})
    public void serviceRemoved(ServiceEvent event) {
        String ski = nameToSki.remove(event.getName());
        if (ski == null) {
            return;
        }
        DiscoveredService removed = bySki.remove(ski);
        if (removed != null) {
            byCommunicationAddress.remove(removed.communicationAddress());
        }
        logger.debug("EEBus mDNS service removed: {} (ski={})", event.getName(), ski);
    }

    @Override
    @NonNullByDefault({})
    public void serviceResolved(ServiceEvent event) {
        ServiceInfo info = event.getInfo();
        EEBusShipTxtRecord txt = EEBusShipTxtRecord.from(info);
        if (txt == null) {
            return;
        }
        String communicationAddress = ipAndPort(info);
        if (communicationAddress == null) {
            logger.debug("EEBus mDNS service '{}' resolved without a usable IP address, ignoring", event.getName());
            return;
        }

        DiscoveredService service = new DiscoveredService(txt.ski(), communicationAddress, event.getName(), txt.brand(),
                txt.model(), txt.type());

        bySki.put(txt.ski(), service);
        byCommunicationAddress.put(communicationAddress, service);
        nameToSki.put(event.getName(), txt.ski());

        logger.debug("EEBus mDNS service resolved: {} -> ski={}, address={}", event.getName(), txt.ski(),
                communicationAddress);
    }

    /**
     * Unregisters this listener from the shared {@link MDNSClient}. Does <strong>not</strong>
     * close the underlying {@code JmDNS} instance(s) - those are owned and lifecycle-managed by
     * openHAB core (shared across all bindings), not by this class.
     */
    @Override
    public void close() {
        mdnsClient.removeServiceListener(SERVICE_TYPE_SHIP_MDNS, this);
    }

    /**
     * Builds the same {@code "ip:port"} / {@code "[ipv6]:port"} string that jeebus.ship uses
     * internally as {@code communicationAddress} - see class javadoc "Verified string format".
     */
    private static @Nullable String ipAndPort(ServiceInfo info) {
        int port = info.getPort();
        Inet4Address[] v4 = info.getInet4Addresses();
        if (v4.length > 0) {
            return v4[0].getHostAddress() + ":" + port;
        }
        Inet6Address[] v6 = info.getInet6Addresses();
        if (v6.length > 0) {
            return "[" + v6[0].getHostAddress() + "]:" + port;
        }
        return null;
    }
}
