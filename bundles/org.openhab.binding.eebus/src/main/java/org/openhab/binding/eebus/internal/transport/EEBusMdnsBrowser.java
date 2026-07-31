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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
 * CONCEPT.md §4.1, §5.4 open item 2, §7 item 9):
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
 * {@code EEBusMdnsDiscoveryParticipant}, which needs no active {@code eebus:oh-device} session
 * and starts scanning as soon as the binding is installed. This class keeps its other,
 * genuinely session-bound purpose: maintaining a {@code communicationAddress -> SKI} map for an
 * <em>active</em> {@code eebus:oh-device} identity, so that
 * {@code UseCasePartner#getCommunicationAddress()} can be resolved back to a paired
 * {@code eebus:oh-entity} Thing. Both classes register their own listener against the same shared
 * {@link MDNSClient}/JmDNS instance(s) - this is <strong>not</strong> redundant: no second
 * JmDNS responder or duplicate network traffic is created, only two independent listener
 * registrations for the same events, each serving a different, non-overlapping purpose.
 * </p>
 *
 * <p>
 * <strong>Verified string format</strong>: {@code communicationAddress} is
 * {@code "<ipv4-or-ipv6>:<port>"} - no brackets around IPv6, even though it contains colons
 * itself (revised 2026-08-21: an earlier version of this class used {@code "[<ipv6>]:<port>"}
 * here, based on {@code ServiceRegistry#getIpAndPort} in {@code org.openmuc.jeebus:ship:2.2.0},
 * github.com/openmuc/jeebus.ship tag {@code v2.2.0} - but the actually-embedded runtime version
 * builds this string via {@code WebSocketHandler#getRemoteAddress()}, confirmed unbracketed;
 * see "Live vs. advertised address mismatch" below). This class's {@link #ipAndPort} builds the
 * same unbracketed format so the {@link #byCommunicationAddress} exact-match fast path can
 * actually hit.
 * </p>
 *
 * <p>
 * <strong>Live vs. advertised address mismatch (found 2026-08-21, real dual-Bridge test):</strong>
 * the string above is only what jeebus.ship's mDNS TXT record <em>advertises</em> - the
 * fixed SHIP server listen port on whichever address the announcing side chose to publish. A
 * live {@code UseCasePartner#getCommunicationAddress()} instead reports
 * {@code WebSocketHandler#getRemoteAddress()}'s actual socket peer address for that one
 * connection (verified against jeebus.ship source: {@code String.format("%s:%d",
 * remoteAddr.getAddress().getHostAddress(), remoteAddr.getPort())}, no brackets even for
 * IPv6). Whenever <em>this</em> {@code eebus:oh-device} is the SHIP <strong>server</strong> side
 * of a connection (i.e. the remote peer dialed in), that reported "remote" port is the peer's
 * ephemeral outbound source port, not the fixed advertised port - it can never equal the mDNS
 * cache key. The observed real-world address also frequently differs in scope: a link-local
 * IPv6 address with a zone-id suffix (e.g. {@code "fe80:0:0:0:e95a:1465:d666:88d%2:36154"})
 * where mDNS advertised a routable/ULA address, or vice versa. {@link
 * #skiForCommunicationAddress} therefore falls back to a host-only match (port and IPv6
 * zone-id stripped) against every address mDNS resolved for a service, not just the one
 * preferred address used to build the advertised string - see {@link #hostOf} and {@link
 * #allHosts}. This is a binding-only workaround: neither jeebus.ship nor jeebus.spine expose a
 * live connection's peer SKI through any API reachable from this binding (checked: {@code
 * ShipConnectionInterface#getRemoteSki()} exists but nothing hands this binding that interface
 * instance or a connection-opened callback - both are internal to jeebus.ship's/jeebus.spine's
 * own {@code ShipCommunication}), and jeebus.ship/jeebus.spine must not be modified without
 * prior human approval (see project instructions).
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
    /**
     * Supplies this node's own SKI (see {@link #EEBusMdnsBrowser(MDNSClient, Supplier)}) - used
     * by {@link #serviceResolved} to filter out this bridge's own mDNS self-announcement.
     * Without this filter, a resolved self-announcement can silently overwrite a real peer's
     * entry in {@link #byHost} whenever both happen to share the same host address (e.g. two
     * {@code eebus:oh-device} Bridges paired on the same machine/IP for testing, or - more
     * generally - any deployment where more than one EEBUS device sits behind the same host
     * address) - see the 2026-08-21 investigation notes (project memory) for the concrete
     * failure this caused: a live SPINE connection's ephemeral remote address resolved, via the
     * host-only fallback, to this node's *own* SKI instead of the actual remote peer's SKI.
     * <p>
     * A <em>supplier</em>, not a plain {@code String}: this class is now constructed by
     * {@code EEBusHandler#startShipSpine()} <strong>before</strong> {@code Device.build()}
     * connects (see the 2026-08-21 "one-shot Discovery notification race" investigation notes,
     * project memory) - deliberately, so it already exists for the entire duration of SPINE's
     * one-shot, non-retried {@code UseCasePartner} discovery, closing the race where this
     * bridge's own {@code EEBusHandler#ohPeerHandlerForCommunicationAddress} resolver saw
     * {@code mdnsBrowser == null} and silently, permanently skipped registering the write-path
     * listeners for a partner. At that earlier construction time, {@code ShipCommunication}'s
     * own SKI is not yet determinable ({@code ShipCommunication#getOwnSki()} throws until its
     * {@code connect()} has run) - so the actual SKI is fetched lazily, once, the first time
     * {@link #serviceResolved} needs it after {@code connect()} has had time to complete.
     */
    private final Supplier<String> ownSkiSupplier;
    private final Map<String, DiscoveredService> byCommunicationAddress = new ConcurrentHashMap<>();
    /**
     * Fallback index used by {@link #skiForCommunicationAddress} when the exact
     * {@code byCommunicationAddress} lookup misses - keyed by bare host (no port, no IPv6
     * zone-id), each host mapping to every distinct SKI currently known to live there (see
     * class javadoc "Live vs. advertised address mismatch"). More than one entry for the same
     * host is a real, expected case - e.g. more than one {@code eebus:oh-device} Bridge/peer
     * sharing one machine's IP, as in this binding's own two-Bridge self-test rig - kept as a
     * map of SKI to service so {@link #skiForCommunicationAddress} can tell "no candidate"
     * apart from "ambiguous, more than one candidate" instead of one silently overwriting
     * another. Previously a single {@code Map<String, DiscoveredService>} keyed only by host,
     * so the *second* same-host service to resolve always silently replaced the first - found
     * live 2026-08-27 (see project memory: this is what let the Controllable System side's
     * {@code onEnergyGuardFound} resolution race the mDNS browser's own catch-up instead of
     * failing safe). See docs/ADR/030-energy-guard-resolution-retry-and-byhost-disambiguation.md.
     */
    private final Map<String, Map<String, DiscoveredService>> byHost = new ConcurrentHashMap<>();
    private final Map<String, DiscoveredService> bySki = new ConcurrentHashMap<>();
    /**
     * SHIP id (mDNS TXT {@code id}) -> service. Since jeebus.ship 3.2.1 the SPINE
     * {@code communicationAddress} of a partner is its SHIP id (e.g. {@code OPHAB-Energy Guard-0001},
     * {@code S10-492011009322}), no longer {@code ip:port} - so the address based lookups alone never match.
     */
    private final Map<String, DiscoveredService> byShipId = new ConcurrentHashMap<>();
    /** mDNS event name -> SKI, needed to resolve serviceRemoved() events (no TXT record there). */
    private final Map<String, String> nameToSki = new ConcurrentHashMap<>();

    /**
     * @param mdnsClient openHAB core's shared mDNS service (injected, see
     *            {@code org.openhab.binding.eebus.internal.handler.EEBusHandlerFactory}/
     *            {@code org.openhab.binding.eebus.internal.handler.EEBusHandler}) - no longer
     *            creates its own {@code JmDNS} instance.
     * @param ownSkiSupplier resolves this bridge's own SKI ({@code ShipCommunication#getOwnSki()})
     *            on demand, used to filter this bridge's own mDNS self-announcement out of every
     *            table this class builds - see {@link #ownSkiSupplier}'s javadoc for why this is
     *            a supplier rather than a plain value, and for why that filtering matters.
     */
    public EEBusMdnsBrowser(MDNSClient mdnsClient, Supplier<String> ownSkiSupplier) {
        this.mdnsClient = mdnsClient;
        this.ownSkiSupplier = ownSkiSupplier;
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
        DiscoveredService exact = byCommunicationAddress.get(communicationAddress);
        if (exact != null) {
            return Optional.of(exact.ski());
        }
        DiscoveredService byId = byShipId.get(communicationAddress);
        if (byId != null) {
            return Optional.of(byId.ski());
        }
        // Fast path missed - almost always the case for a live SPINE connection, see class
        // javadoc "Live vs. advertised address mismatch". Fall back to host-only matching.
        String host = hostOf(communicationAddress);
        if (host == null) {
            logger.debug("communicationAddress '{}' has no parseable host part, cannot resolve", communicationAddress);
            return Optional.empty();
        }
        Map<String, DiscoveredService> candidates = byHost.getOrDefault(host, Map.of());
        if (candidates.isEmpty()) {
            logger.debug(
                    "communicationAddress '{}' (host '{}') matched neither the advertised address nor any known mDNS host",
                    communicationAddress, host);
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            // Cannot safely pick one - see #byHost's javadoc. Fail closed rather than silently
            // guessing which of several same-host peers this address actually belongs to.
            logger.debug(
                    "communicationAddress '{}' (host '{}') is ambiguous - {} distinct SKIs share this host ({}), "
                            + "cannot disambiguate by host alone",
                    communicationAddress, host, candidates.size(), candidates.keySet());
            return Optional.empty();
        }
        return Optional.of(candidates.values().iterator().next().ski());
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
            byShipId.values().removeIf(s -> ski.equals(s.ski()));
            byHost.values().forEach(hostCandidates -> hostCandidates.remove(ski));
            byHost.values().removeIf(Map::isEmpty);
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

        String currentOwnSki;
        try {
            currentOwnSki = ownSkiSupplier.get();
        } catch (RuntimeException e) {
            // This bridge's own SHIP connect() has not completed yet (see #ownSkiSupplier's
            // javadoc) - at this point in time it cannot itself have self-announced yet either,
            // so there is nothing of "our own" to filter out of *this* event. Falling through to
            // catalog the event normally is correct and safe, not a fallback to be worried about.
            currentOwnSki = null;
        }
        if (currentOwnSki != null && currentOwnSki.equals(txt.ski())) {
            // Our own SHIP server's mDNS self-announcement - openHAB's shared MDNSClient does
            // not filter this out for us. Cataloging it here would let it silently clobber a
            // real peer's byHost entry whenever both share the same host address (see
            // #ownSkiSupplier's javadoc) - this method is only ever meant to resolve *remote*
            // peers, so self is simply not a useful entry to keep at all.
            logger.debug("EEBus mDNS service '{}' is this bridge's own self-announcement (ski={}), ignoring",
                    event.getName(), txt.ski());
            return;
        }

        DiscoveredService service = new DiscoveredService(txt.ski(), communicationAddress, event.getName(), txt.brand(),
                txt.model(), txt.type());
        if (!txt.id().isBlank()) {
            byShipId.put(txt.id(), service);
        }

        bySki.put(txt.ski(), service);
        byCommunicationAddress.put(communicationAddress, service);
        List<String> hosts = allHosts(info);
        for (String host : hosts) {
            Map<String, DiscoveredService> servicesForHost = byHost.computeIfAbsent(host,
                    h -> new ConcurrentHashMap<>());
            if (servicesForHost != null) {
                servicesForHost.put(txt.ski(), service);
            }
        }
        nameToSki.put(event.getName(), txt.ski());

        logger.debug("EEBus mDNS service resolved: {} -> ski={}, address={}, hosts={}", event.getName(), txt.ski(),
                communicationAddress, hosts);
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
     * Builds the same {@code "ip:port"} string that jeebus.ship's live {@code
     * WebSocketHandler#getRemoteAddress()} produces - see class javadoc "Live vs. advertised
     * address mismatch". Deliberately <strong>not</strong> bracketed for IPv6 (an earlier
     * version used {@code "[ipv6]:port"}, which never matched the live, unbracketed format -
     * this only mattered for the {@code byCommunicationAddress} exact-match fast path, since
     * {@link #hostOf}/{@link #byHost} do not care about brackets either way).
     */
    private static @Nullable String ipAndPort(ServiceInfo info) {
        int port = info.getPort();
        Inet4Address[] v4 = info.getInet4Addresses();
        if (v4.length > 0) {
            return v4[0].getHostAddress() + ":" + port;
        }
        Inet6Address[] v6 = info.getInet6Addresses();
        if (v6.length > 0) {
            return v6[0].getHostAddress() + ":" + port;
        }
        return null;
    }

    /**
     * Every address (IPv4 and IPv6 alike, zone-id stripped) mDNS resolved for this service -
     * unlike {@link #ipAndPort}, which only builds one preferred advertised address string,
     * this collects all of them so {@link #byHost} can match a live connection arriving on
     * <em>any</em> of the peer's interfaces/scopes, not just the one address mDNS happened to
     * prefer.
     */
    private static List<String> allHosts(ServiceInfo info) {
        List<String> hosts = new ArrayList<>();
        for (Inet4Address addr : info.getInet4Addresses()) {
            hosts.add(addr.getHostAddress());
        }
        for (Inet6Address addr : info.getInet6Addresses()) {
            hosts.add(stripZone(addr.getHostAddress()));
        }
        return hosts;
    }

    /**
     * Extracts the bare host part (no port, no IPv6 zone-id) from a {@code "host:port"}
     * communicationAddress string, so it can be looked up in {@link #byHost}. Handles both this
     * class's own {@link #ipAndPort} format and jeebus.ship's actual live format (see class
     * javadoc "Live vs. advertised address mismatch") - unbracketed {@code
     * InetAddress#getHostAddress()} + ":" + port, e.g.
     * {@code "fe80:0:0:0:e95a:1465:d666:88d%2:36154"}. Splitting on the <em>last</em> colon
     * always isolates the numeric port correctly, because an IPv6 zone-id uses {@code "%"}, not
     * {@code ":"} - so the last colon can never be part of one.
     *
     * @return the bare host, or {@code null} if {@code communicationAddress} has no parseable
     *         {@code "...:<port>"} suffix (defensive - not expected to happen with either source
     *         of this string)
     */
    private static @Nullable String hostOf(String communicationAddress) {
        String address = communicationAddress;
        if (address.startsWith("[")) {
            int closeBracket = address.indexOf(']');
            if (closeBracket < 0) {
                return null;
            }
            return stripZone(address.substring(1, closeBracket));
        }
        int lastColon = address.lastIndexOf(':');
        if (lastColon < 0) {
            return null;
        }
        String port = address.substring(lastColon + 1);
        if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return stripZone(address.substring(0, lastColon));
    }

    /** Strips a trailing IPv6 zone-id (e.g. {@code "%2"}) if present; a no-op for IPv4. */
    private static String stripZone(String host) {
        int percent = host.indexOf('%');
        return percent < 0 ? host : host.substring(0, percent);
    }
}
