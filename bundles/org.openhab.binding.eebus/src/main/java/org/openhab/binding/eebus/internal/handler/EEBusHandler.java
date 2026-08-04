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
package org.openhab.binding.eebus.internal.handler;

import static org.openmuc.jeebus.shipspine.ShipCommunication.ConnectClientsTo.NONE;
import static org.openmuc.jeebus.shipspine.ShipCommunication.ConnectClientsTo.TRUSTED;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.config.EEBusConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusPeerConfiguration;
import org.openhab.binding.eebus.internal.transport.EEBusLpcServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLppServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser;
import org.openhab.binding.eebus.internal.transport.EEBusMetadataService;
import org.openhab.binding.eebus.internal.transport.EEBusMpcClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMpcServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusPortPool;
import org.openhab.core.OpenHAB;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openmuc.jeebus.ship.api.ShipNodeConfiguration;
import org.openmuc.jeebus.shipspine.ShipCommunication;
import org.openmuc.jeebus.spine.api.Device;
import org.openmuc.jeebus.spine.spi.UseCase;
import org.openmuc.jeebus.spine.xsd.v1.DeviceTypeEnumType;
import org.openmuc.jeebus.spine.xsd.v1.EntityTypeEnumType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusHandler} is the Bridge handler for the {@code eebus:service} Thing
 * type. It represents exactly one local SHIP/SPINE service instance: one certificate/SKI,
 * one mDNS-advertised presence, one SPINE {@code Device}.
 *
 * <p>
 * <strong>Naming note:</strong> this class is the Bridge handler (what CONCEPT.md calls
 * {@code EEBusBridgeHandler}). It kept the archetype's original file/class name
 * {@code EEBusHandler} to avoid an extra rename step; feel free to rename via your IDE's
 * refactor tool if you'd rather match the CONCEPT.md naming exactly.
 * </p>
 *
 * <p>
 * <strong>Pairing model</strong> (see CONCEPT.md §5.2, §6 decision 4): there is no
 * separate approve/reject action. Trust is derived purely from which {@code eebus:peer}
 * child Things exist — see {@link #recomputeTrustedSkis()}, called whenever a child Thing
 * is added or removed. Creating a peer Thing pairs it; removing it revokes trust on the
 * next restart/recompute.
 * </p>
 *
 * <p>
 * <strong>Verified</strong> (CONCEPT.md §7 item 1, resolved): {@link ShipNodeConfiguration}'s
 * certPath constructor does auto-create the certificate/keystore if none exists at the given
 * path, confirmed against the actual {@code org.openmuc.jeebus:ship:2.2.0} source
 * (github.com/openmuc/jeebus.ship, tag {@code v2.2.0}). No custom certificate persistence
 * code was needed.
 * </p>
 *
 * <p>
 * <strong>Package note:</strong> this class still imports jEEBus.SHIP/SPINE types directly
 * (starting/tearing down {@link ShipCommunication}, building the local SPINE {@link Device}) -
 * it is not yet a pure openHAB-facing class despite living in {@code internal.handler}. A
 * follow-up could extract that session-management logic into a dedicated
 * {@code internal.transport} class so {@code internal.handler} becomes fully free of SPINE/SHIP
 * imports; not done in this pass to avoid a larger, unreviewed behavioral refactor. See
 * docs/ADR/002-package-split-transport-handler.md.
 * </p>
 *
 * <p>
 * <strong>Start/dispose concurrency</strong> (see docs/ADR/005-supersede-stale-start-on-dispose.md):
 * {@link #startShipSpine} runs asynchronously on a background task scheduled by
 * {@link #initialize()} and performs the SHIP server's actual port bind
 * ({@code communication.connect()}) without holding any lock, since that call can block for a
 * moment and must never stall {@link #dispose()}. Instead, {@link #initialize()} and
 * {@link #dispose()} each bump a generation counter kept in {@link #LIFECYCLES}, a static map
 * keyed by {@link ThingUID}; right after connecting, {@link #startShipSpine} re-checks that
 * counter before publishing {@link #shipCommunication} etc. If it has been superseded in the
 * meantime, it shuts the SHIP server it just bound back down instead of publishing it - so a
 * stale attempt can still finish binding a moment after {@link #dispose()} ran, but it always
 * cleans up after itself rather than leaking the port. {@link #dispose()} itself never blocks
 * waiting for an in-flight start attempt. The counter is keyed by {@link ThingUID} rather than
 * kept as a plain instance field because a live reproduction showed {@code startShipSpine()}
 * running twice for the same Thing with neither attempt detecting the other - i.e. openHAB can
 * end up with more than one {@link EEBusHandler} object for the same Thing, each with its own
 * instance state; only state shared by Thing identity catches that case. See ADR-005 for the full
 * history, including why an earlier, join-based design and a plain instance-field generation
 * counter were both rejected.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusHandler extends BaseBridgeHandler {

    /**
     * Per-Thing lifecycle state for the generation-counter scheme described in the class
     * javadoc and ADR-005. Kept in a static map keyed by {@link ThingUID} - not as a plain
     * instance field - specifically because two separate {@link EEBusHandler} objects for the
     * same Thing were observed to race each other in a live reproduction; instance fields would
     * not be shared between them, but this map is.
     */
    @NonNullByDefault
    private static final class Lifecycle {
        final Object lock = new Object();
        long generation;
    }

    private static final Map<ThingUID, Lifecycle> LIFECYCLES = new ConcurrentHashMap<>();

    private final Logger logger = LoggerFactory.getLogger(EEBusHandler.class);

    private @Nullable EEBusConfiguration config;
    private @Nullable ShipCommunication shipCommunication;
    private @Nullable Device device;
    private @Nullable EEBusMdnsBrowser mdnsBrowser;

    /**
     * The port taken out of {@link #portPool} by {@link #initialize()} (either the configured
     * port or a free one assigned by the pool) - released back to the pool in
     * {@link #handleRemoval()}, never in {@link #dispose()}. See {@link EEBusPortPool}'s class
     * javadoc for the lifecycle contract.
     */
    private @Nullable Integer reservedPort;

    private final EEBusMetadataService metadataService;
    private final MDNSClient mdnsClient;
    private final EEBusPortPool portPool;

    public EEBusHandler(Bridge bridge, EEBusMetadataService metadataService, MDNSClient mdnsClient,
            EEBusPortPool portPool) {
        super(bridge);
        this.metadataService = metadataService;
        this.mdnsClient = mdnsClient;
        this.portPool = portPool;
    }

    /**
     * @return the {@link Lifecycle} shared by every {@link EEBusHandler} object that has ever
     *         existed for this Thing's UID, creating it on first use.
     */
    private Lifecycle lifecycle() {
        // Map#computeIfAbsent is annotated as returning @Nullable (the mapping function is
        // allowed to return null, in which case no mapping is added) even though our mapping
        // function here never does - same pattern as EEBusMetadataService#itemNameOf.
        return Objects.requireNonNull(LIFECYCLES.computeIfAbsent(thing.getUID(), uid -> new Lifecycle()));
    }

    @Override
    public void initialize() {
        // Bump the generation before doing anything else, so that any background start task
        // from a previous initialize() (that has not yet reached the check in startShipSpine())
        // - whether scheduled by this object or, per the class javadoc, a different EEBusHandler
        // object for the same Thing - is guaranteed to see itself as superseded. See ADR-005.
        Lifecycle lifecycle = lifecycle();
        long generation;
        synchronized (lifecycle.lock) {
            generation = ++lifecycle.generation;
        }

        // Diagnostic instrumentation for the still-open "why does initialize() run more than once
        // for the same Thing without an intervening dispose()" question (see ADR-005's "Negative"
        // section). Deliberately no trailing Throwable here (an earlier version printed one purely
        // to capture the caller's stack trace, with message "no error" - dropped since it reads
        // like a real exception in the log and was confusing to read at a glance).
        logger.debug("initialize() called for {} (handler={}, generation={}, thread={})", thing.getUID(),
                System.identityHashCode(this), generation, Thread.currentThread().getName());

        EEBusConfiguration cfg = getConfigAs(EEBusConfiguration.class);
        this.config = cfg;

        if (cfg.vendorCode.isBlank() || cfg.deviceBrand.isBlank() || cfg.deviceModel.isBlank()
                || cfg.serialNumber.isBlank() || cfg.mdnsServiceInstance.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "vendorCode, deviceBrand, deviceModel, serialNumber and mdnsServiceInstance are required");
            return;
        }

        Optional<Integer> resolvedPortOrEmpty = resolvePort(cfg);
        if (resolvedPortOrEmpty.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "No free port available in the EEBus port pool (" + EEBusPortPool.PORT_RANGE_START + "-"
                            + EEBusPortPool.PORT_RANGE_END + "); configure a port explicitly");
            return;
        }
        int resolvedPort = resolvedPortOrEmpty.get();
        this.reservedPort = resolvedPort;

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            try {
                if (startShipSpine(cfg, resolvedPort, lifecycle, generation)) {
                    updateStatus(ThingStatus.ONLINE);
                }
                // else: superseded while starting - startShipSpine() already shut back down
                // whatever it just bound; whichever newer attempt is current now owns the
                // ThingStatus.
            } catch (Exception e) {
                boolean stillCurrent;
                synchronized (lifecycle.lock) {
                    stillCurrent = generation == lifecycle.generation;
                }
                if (stillCurrent) {
                    logger.warn("Failed to start EEBus service instance '{}' (generation={}, handler={})",
                            thing.getUID(), generation, System.identityHashCode(this), e);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                } else {
                    logger.debug(
                            "EEBus service instance '{}' failed to start after being superseded by a newer "
                                    + "attempt; ignoring (generation={}, handler={}): {}",
                            thing.getUID(), generation, System.identityHashCode(this), e.getMessage());
                }
            }
        });
    }

    /**
     * Resolves the port to bind the local SHIP server to, taking it out of {@link #portPool}: the
     * Thing's explicitly configured port if one was set, otherwise a free port assigned by the
     * pool. Called once from {@link #initialize()} - the resolved port is only released back to
     * the pool in {@link #handleRemoval()}, never {@link #dispose()} (see {@link EEBusPortPool}'s
     * class javadoc for why).
     *
     * @param cfg this Thing's configuration
     * @return the resolved port; empty if no port was configured and the pool
     *         ({@value EEBusPortPool#PORT_RANGE_START}-{@value EEBusPortPool#PORT_RANGE_END}) is
     *         exhausted
     */
    private Optional<Integer> resolvePort(EEBusConfiguration cfg) {
        Integer configuredPort = cfg.port;
        if (configuredPort == null) {
            return portPool.acquireFreePort();
        }
        portPool.reservePort(configuredPort);
        return Optional.of(configuredPort);
    }

    /**
     * @param cfg
     *            the configuration to start the SHIP/SPINE service instance with
     * @param resolvedPort
     *            the port to bind the SHIP server to, as resolved by {@link #resolvePort} in
     *            {@link #initialize()}
     * @param lifecycle
     *            this Thing's shared {@link Lifecycle}, see ADR-005
     * @param myGeneration
     *            the {@code lifecycle.generation} value captured by the caller when this attempt
     *            was scheduled - used to detect being superseded, see ADR-005
     * @return {@code true} if this attempt published itself as the active instance
     *         ({@link #shipCommunication} etc. were set); {@code false} if it was superseded while
     *         connecting and shut itself back down instead
     */
    private boolean startShipSpine(EEBusConfiguration cfg, int resolvedPort, Lifecycle lifecycle, long myGeneration)
            throws Exception {
        // CONCEPT §7.1 - verified against the actual org.openmuc.jeebus:ship:2.2.0 source
        // (github.com/openmuc/jeebus.ship, tag v2.2.0): the ShipNodeConfiguration constructor
        // that takes a certPath auto-creates the certificate/keystore at that path if none is
        // found there yet (see its javadoc: "If there is no certificate found at the location,
        // a new certificate will be created ... at the location"). No custom persistence code
        // needed here.
        File keystoreFile = getKeystoreFile();
        keystoreFile.getParentFile().mkdirs();

        String shipId = cfg.vendorCode + "-" + cfg.deviceModel + "-" + cfg.serialNumber;
        String distinguishedName = "CN=" + cfg.deviceModel + "-" + cfg.serialNumber;

        // Constructor signature confirmed against the real ship:2.2.0 source (see above):
        // ipAddress, port, wssPath, keepAlive, serviceId, serviceDomain, serviceInstance, alias,
        // certPath, keyStorePassphrase, keyPairPassphrase, distinguishedName, certificateValidityInDays.
        // Note: the 4th parameter is "keepAlive" (TCP keep-alive), NOT autoAccept - autoAccept is
        // wired separately below via ShipCommunication#withAutoAcceptMode.
        ShipNodeConfiguration nodeConfig = new ShipNodeConfiguration("0.0.0.0", resolvedPort, "/ship/", true, shipId,
                "local.", cfg.mdnsServiceInstance, "eebus", keystoreFile.getAbsolutePath(), new char[0], new char[0],
                distinguishedName, 3650);

        // cfg.connectToPeers defaults to true (normal SHIP behavior: dial trusted peers as soon
        // as mDNS discovers them, in addition to accepting their connections). Set to false only
        // as a diagnostic workaround when pairing two self-built instances against each other -
        // see EEBusConfiguration#connectToPeers and TEST_PAIRING.md (Test 2, "Known Bug
        // Encountered") for why: with both sides dialing out, a bug in the embedded SHIP
        // library's simultaneous-connection handling can abort the handshake.
        ShipCommunication communication = new ShipCommunication(nodeConfig).withTrustedSkis(currentPeerSkis())
                .withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE).withAutoAcceptMode(cfg.autoAcceptEnabled);

        // CONCEPT §5.5/§7.8: server-role UseCase implementations are attached here, one per
        // entry in cfg.supportedUseCasesServer. Only MPC is implemented so far
        // (EEBusMpcServerUseCase, matches the user's original "Wechselrichter/ImSys"
        // scenario) - the other core use cases remain logged-but-unimplemented.
        List<UseCase> serverUseCases = new ArrayList<>();
        if (cfg.supportedUseCasesServer.contains("MPC")) {
            serverUseCases.add(new EEBusMpcServerUseCase(metadataService));
        }
        if (cfg.supportedUseCasesServer.contains("LPC")) {
            serverUseCases.add(new EEBusLpcServerUseCase(metadataService));
        }
        if (cfg.supportedUseCasesServer.contains("LPP")) {
            serverUseCases.add(new EEBusLppServerUseCase(metadataService));
        }
        List<String> unimplementedServerUseCases = cfg.supportedUseCasesServer.stream()
                .filter(useCase -> !Set.of("MPC", "LPC", "LPP").contains(useCase)).toList();
        if (!unimplementedServerUseCases.isEmpty()) {
            logger.info("Configured to offer use cases {} - not yet implemented, see CONCEPT.md §7.8",
                    unimplementedServerUseCases);
        }

        // CONCEPT §5.4/§7.3: client-role UseCase implementations (peer use-case detection) are
        // attached the same way - added to the same local entity via withUseCases(...), see
        // jeebus.spine's demo ExampleUseCase (Entity#addUseCase() fills @Inject fields and calls
        // setup() regardless of client/server role). Only MPC is implemented so far
        // (EEBusMpcClientUseCase, the consumer-side counterpart of EEBusMpcServerUseCase).
        List<UseCase> clientUseCases = new ArrayList<>();
        if (cfg.supportedUseCasesClient.contains("MPC")) {
            clientUseCases.add(new EEBusMpcClientUseCase(metadataService, this::peerThingUidForCommunicationAddress));
        }
        List<String> unimplementedClientUseCases = cfg.supportedUseCasesClient.stream()
                .filter(useCase -> !"MPC".equals(useCase)).toList();
        if (!unimplementedClientUseCases.isEmpty()) {
            logger.info("Configured to detect use cases {} - not yet implemented, see CONCEPT.md §7.3",
                    unimplementedClientUseCases);
        }

        List<UseCase> allUseCases = new ArrayList<>(serverUseCases);
        allUseCases.addAll(clientUseCases);

        // The actual network I/O - generates/loads the certificate and binds the SHIP server's
        // port - happens inside build() below: DeviceBuilder.build() calls
        // buildWithoutConnecting() and then device.connect(), which calls
        // Communication.connect() (= ShipCommunication.connect()) internally (confirmed by
        // decompiling the actually-embedded spine-4.0.1 classes, since the checked-out
        // jeebus.spine source did not match). No separate, explicit connect() call is needed or
        // wanted here - one used to exist right after this block, calling
        // communication.connect() a second time on the same, already-connected instance. That
        // was a genuine bug: build() already binds the port, so the second call always bound the
        // same port again and reliably failed with BindException - deterministically, regardless
        // of which port was configured, which is what eventually gave it away (see the
        // investigation that found this, referenced from the git history around this change).
        // Deliberately not holding lifecycle.lock here: this can take a moment, and holding
        // the lock across it would block dispose() (see ADR-005 for why that is unacceptable -
        // it collides with openHAB's own SafeCaller timeout). This means a concurrent dispose()
        // or newer initialize() - on this object or, per the class javadoc, a different
        // EEBusHandler object for the same Thing - can bump the generation while this call is in
        // flight; that is detected and handled immediately below.
        // Diagnostic instrumentation, see the matching comment in initialize(). Marks the moment
        // the actual port bind is attempted, so it can be correlated by generation/handler with
        // the initialize()/dispose() log lines and the eventual success/BindException outcome.
        logger.debug("{}: startShipSpine() about to bind (generation={}, handler={}, thread={})", thing.getUID(),
                myGeneration, System.identityHashCode(this), Thread.currentThread().getName());

        Device localDevice = Device.getBuilder().withDeviceType(DeviceTypeEnumType.ENERGY_MANAGEMENT_SYSTEM)
                .withCommunication(communication).withId("d:_n:" + shipId).withDiscoverDevices(true).addEntity()
                .setType(EntityTypeEnumType.CEM).withUseCases(allUseCases.toArray(new UseCase[0])).applyToDevice()
                .build();

        synchronized (lifecycle.lock) {
            if (myGeneration != lifecycle.generation) {
                // Superseded by a newer initialize()/dispose() while we were connecting. Do not
                // publish this instance anywhere reachable - shut the SHIP server we just bound
                // back down instead, so its port is not leaked. See ADR-005.
                logger.debug("EEBus service instance '{}' was disposed/reconfigured while starting; shutting the "
                        + "now-superseded SHIP server back down", thing.getUID());
                communication.disconnect();
                localDevice.close();
                return false;
            }

            this.shipCommunication = communication;
            this.device = localDevice;
            // CONCEPT §4.1/§7.9: own mDNS browser for _ship._tcp.local., since jeebus.ship's own
            // mDNS classes (org.openmuc.jeebus.ship.node.service.*) are not OSGi-exported and
            // ShipCommunication does not expose raw mDNS events. Built on openHAB core's shared
            // MDNSClient service (not a private JmDNS instance) - see EEBusMdnsBrowser's class
            // javadoc. Used for (a) a future discovery inbox with friendly names (requirement 3)
            // and (b) resolving UseCasePartner#getCommunicationAddress() back to a peer's SKI
            // (§7.2).
            this.mdnsBrowser = new EEBusMdnsBrowser(mdnsClient);
            logger.debug("{}: startShipSpine() bound and published successfully (generation={}, handler={})",
                    thing.getUID(), myGeneration, System.identityHashCode(this));
        }

        updateProperty(EEBusBindingConstants.PROPERTY_LOCAL_SKI, communication.getOwnSki());
        return true;
    }

    @Override
    public void dispose() {
        // Deliberately non-blocking (see ADR-005: an earlier design that waited here for an
        // in-flight startShipSpine() to finish collided with openHAB's own SafeCaller timeout on
        // dispose(), which made things worse, not better). Bumping the generation is enough: any
        // background task - from this object or, per the class javadoc, a different EEBusHandler
        // object for the same Thing - that is still connecting will see the mismatch when it
        // re-checks right after connect() returns, and will shut itself back down instead of
        // publishing - see startShipSpine(). Here, we only ever need to close whatever this
        // object instance already published before this call started.
        @Nullable
        ShipCommunication communication;
        @Nullable
        Device localDevice;
        @Nullable
        EEBusMdnsBrowser browser;
        Lifecycle lifecycle = lifecycle();
        long generation;
        synchronized (lifecycle.lock) {
            generation = ++lifecycle.generation;
            communication = this.shipCommunication;
            localDevice = this.device;
            browser = this.mdnsBrowser;
            this.shipCommunication = null;
            this.device = null;
            this.mdnsBrowser = null;
        }

        // Diagnostic instrumentation, see the matching comment in initialize(). "hadCommunication"
        // shows whether this dispose() actually found a published instance to tear down, or ran
        // against an object that never got that far.
        logger.debug("dispose() called for {} (handler={}, generation={}, thread={}, hadCommunication={})",
                thing.getUID(), System.identityHashCode(this), generation, Thread.currentThread().getName(),
                communication != null);

        if (communication != null) {
            communication.disconnect();
        }
        if (localDevice != null) {
            // Device extends Shutdownable (AutoCloseable with a no-throws close()) - see
            // org.openmuc.jeebus.spine.api.Shutdownable.
            localDevice.close();
        }
        if (browser != null) {
            browser.close();
        }
    }

    @Override
    public void handleRemoval() {
        // Only discard the shared Lifecycle when the Thing is actually deleted, not on every
        // dispose()/update cycle - see java-coding-rules.md's StorageService lifecycle pattern
        // for the same dispose()-vs-handleRemoval() distinction. LIFECYCLES otherwise grows by
        // one small entry per Thing UID ever created, which is harmless short-term but unbounded
        // over a long-running instance's lifetime.
        LIFECYCLES.remove(thing.getUID());

        // Same dispose()-vs-handleRemoval() distinction applies to the port reserved from
        // portPool in initialize()/resolvePort(): only give it back once the Thing is actually
        // deleted, not on every restart/reconfigure, or a still-running Bridge could lose its
        // port to a different Bridge mid-restart. See EEBusPortPool's class javadoc.
        Integer port = this.reservedPort;
        if (port != null) {
            portPool.releasePort(port);
            this.reservedPort = null;
        }

        updateStatus(ThingStatus.REMOVED);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined on the Bridge itself.
    }

    // Note (ADR-003): this Bridge no longer registers a ThingHandlerService-based discovery
    // service. Real-device discovery is now binding-scoped (EEBusMdnsDiscoveryParticipant,
    // independent of this handler's lifecycle) instead of Bridge-scoped - see CONCEPT.md §4.1.

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        recomputeTrustedSkis();
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        recomputeTrustedSkis();
    }

    /**
     * Recomputes the trusted-SKI set from all currently configured {@code eebus:peer}
     * child Things and pushes it to {@link ShipCommunication}. See CONCEPT.md §5.2: a
     * peer Thing's existence is what constitutes pairing.
     */
    private void recomputeTrustedSkis() {
        ShipCommunication communication = this.shipCommunication;
        if (communication == null) {
            return;
        }
        communication.withTrustedSkis(currentPeerSkis());
    }

    // Note (ADR-003): the public pairedSkis() accessor that used to exist here was removed -
    // its only caller, the old Bridge-scoped EEBusDiscoveryService, is superseded by
    // EEBusMdnsDiscoveryParticipant, which checks all eebus:peer Things across the whole
    // ThingRegistry directly instead of asking one specific eebus:service Bridge.

    /**
     * Resolves a SKI to the {@code eebus:peer} child Thing's UID string, if a Thing with that
     * SKI is currently configured. Used to compose the {@code peerThingUidResolver} passed to
     * {@code EEBusMpcClientUseCase} (CONCEPT.md §7.3/§8): combined with
     * {@link EEBusMdnsBrowser#skiForCommunicationAddress}, this resolves a SPINE
     * {@code communicationAddress} all the way back to the paired peer Thing that
     * {@code eebus="..." [peer="..."]} Item metadata references.
     *
     * @param ski the SKI to look up
     * @return the matching child Thing's UID as a string (matches
     *         {@code Metadata#getConfiguration().get("peer")}), if any
     */
    Optional<String> peerThingUidForSki(String ski) {
        for (Thing child : getThing().getThings()) {
            EEBusPeerConfiguration peerConfig = child.getConfiguration().as(EEBusPeerConfiguration.class);
            if (ski.equals(peerConfig.ski)) {
                return Optional.of(child.getUID().getAsString());
            }
        }
        return Optional.empty();
    }

    /**
     * Composes {@link EEBusMdnsBrowser#skiForCommunicationAddress} with
     * {@link #peerThingUidForSki} - the full {@code communicationAddress -> peer Thing UID}
     * resolver that {@code EEBusMpcClientUseCase} (and future Client-role UseCases) need to
     * route a detected {@code UseCasePartner} back to the Item metadata that references it.
     * Read lazily (not captured at construction time) so it keeps working regardless of
     * {@link #mdnsBrowser}'s startup order relative to Client-UseCase registration.
     */
    private Optional<String> peerThingUidForCommunicationAddress(String communicationAddress) {
        EEBusMdnsBrowser browser = this.mdnsBrowser;
        if (browser == null) {
            return Optional.empty();
        }
        return browser.skiForCommunicationAddress(communicationAddress).flatMap(this::peerThingUidForSki);
    }

    private Set<String> currentPeerSkis() {
        Set<String> skis = new HashSet<>();
        for (Thing child : getThing().getThings()) {
            EEBusPeerConfiguration peerConfig = child.getConfiguration().as(EEBusPeerConfiguration.class);
            if (!peerConfig.ski.isBlank()) {
                skis.add(peerConfig.ski);
            }
        }
        return skis;
    }

    private File getKeystoreFile() {
        return new File(OpenHAB.getUserDataFolder() + File.separator + "eebus",
                thing.getUID().getAsString().replace(':', '_') + ".jks");
    }

    /**
     * @return the SPINE device backing this Bridge, or {@code null} before/after
     *         {@link #initialize()}/{@link #dispose()}. Used by {@link EEBusPeerHandler}
     *         to reach {@code Device#getNodeManagement()}.
     */
    @Nullable
    Device getDevice() {
        return device;
    }

    /**
     * @return the mDNS browser backing this Bridge, or {@code null} before/after
     *         {@link #initialize()}/{@link #dispose()}, or if it failed to start (see
     *         {@link #startShipSpine}). Used by {@link EEBusPeerHandler} to resolve
     *         {@code UseCasePartner#getCommunicationAddress()} back to a peer's SKI
     *         (CONCEPT.md §7.2/§7.9).
     */
    @Nullable
    public EEBusMdnsBrowser getMdnsBrowser() {
        return mdnsBrowser;
    }
}
