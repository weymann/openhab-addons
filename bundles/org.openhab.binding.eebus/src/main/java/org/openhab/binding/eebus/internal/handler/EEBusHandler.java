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

import static org.openmuc.jeebus.shipspine.ShipCommunication.ConnectClientsTo.TRUSTED;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.config.EEBusConfiguration;
import org.openhab.binding.eebus.internal.config.EEBusPeerConfiguration;
import org.openhab.binding.eebus.internal.discovery.EEBusDiscoveryService;
import org.openhab.binding.eebus.internal.transport.EEBusLpcServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusLppServerUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser;
import org.openhab.binding.eebus.internal.transport.EEBusMetadataService;
import org.openhab.binding.eebus.internal.transport.EEBusMpcClientUseCase;
import org.openhab.binding.eebus.internal.transport.EEBusMpcServerUseCase;
import org.openhab.core.OpenHAB;
import org.openhab.core.io.transport.mdns.MDNSClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
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
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(EEBusHandler.class);

    private @Nullable EEBusConfiguration config;
    private @Nullable ShipCommunication shipCommunication;
    private @Nullable Device device;
    private @Nullable EEBusMdnsBrowser mdnsBrowser;

    private final EEBusMetadataService metadataService;
    private final MDNSClient mdnsClient;

    public EEBusHandler(Bridge bridge, EEBusMetadataService metadataService, MDNSClient mdnsClient) {
        super(bridge);
        this.metadataService = metadataService;
        this.mdnsClient = mdnsClient;
    }

    @Override
    public void initialize() {
        EEBusConfiguration cfg = getConfigAs(EEBusConfiguration.class);
        this.config = cfg;

        if (cfg.vendorCode.isBlank() || cfg.deviceBrand.isBlank() || cfg.deviceModel.isBlank()
                || cfg.serialNumber.isBlank() || cfg.mdnsServiceInstance.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "vendorCode, deviceBrand, deviceModel, serialNumber and mdnsServiceInstance are required");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            try {
                startShipSpine(cfg);
                updateStatus(ThingStatus.ONLINE);
            } catch (Exception e) {
                logger.warn("Failed to start EEBus service instance '{}'", thing.getUID(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }

    private void startShipSpine(EEBusConfiguration cfg) throws Exception {
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
        ShipNodeConfiguration nodeConfig = new ShipNodeConfiguration("0.0.0.0", cfg.port, "/ship/", true, shipId,
                "local.", cfg.mdnsServiceInstance, "eebus", keystoreFile.getAbsolutePath(), new char[0], new char[0],
                distinguishedName, 3650);

        ShipCommunication communication = new ShipCommunication(nodeConfig).withTrustedSkis(currentPeerSkis())
                .withConnectClientsTo(TRUSTED).withAutoAcceptMode(cfg.autoAcceptEnabled);
        this.shipCommunication = communication;

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

        Device localDevice = Device.getBuilder().withDeviceType(DeviceTypeEnumType.ENERGY_MANAGEMENT_SYSTEM)
                .withCommunication(communication).withId("d:_n:" + shipId).withDiscoverDevices(true).addEntity()
                .setType(EntityTypeEnumType.CEM).withUseCases(allUseCases.toArray(new UseCase[0])).applyToDevice()
                .build();
        this.device = localDevice;

        communication.connect();

        updateProperty("localSki", communication.getOwnSki());

        // CONCEPT §4.1/§7.9: own mDNS browser for _ship._tcp.local., since jeebus.ship's own
        // mDNS classes (org.openmuc.jeebus.ship.node.service.*) are not OSGi-exported and
        // ShipCommunication does not expose raw mDNS events. Built on openHAB core's shared
        // MDNSClient service (not a private JmDNS instance) - see EEBusMdnsBrowser's class
        // javadoc. Used for (a) a future discovery inbox with friendly names (requirement 3)
        // and (b) resolving UseCasePartner#getCommunicationAddress() back to a peer's SKI
        // (§7.2).
        this.mdnsBrowser = new EEBusMdnsBrowser(mdnsClient);
    }

    @Override
    public void dispose() {
        ShipCommunication communication = this.shipCommunication;
        if (communication != null) {
            communication.disconnect();
        }
        Device localDevice = this.device;
        if (localDevice != null) {
            // Device extends Shutdownable (AutoCloseable with a no-throws close()) - see
            // org.openmuc.jeebus.spine.api.Shutdownable.
            localDevice.close();
        }
        EEBusMdnsBrowser browser = this.mdnsBrowser;
        if (browser != null) {
            browser.close();
        }
        this.shipCommunication = null;
        this.device = null;
        this.mdnsBrowser = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined on the Bridge itself.
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        // Registers EEBusDiscoveryService as a Bridge-scoped ThingHandlerService (CONCEPT.md
        // §7 item 10), verified against the openHAB binding developer docs pattern for
        // "Discovery that is bound to a Bridge".
        return List.of(EEBusDiscoveryService.class);
    }

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

    /**
     * @return the SKIs of all currently configured {@code eebus:peer} child Things. Used by
     *         {@link EEBusDiscoveryService} to exclude already-paired peers from mDNS-based
     *         discovery results (CONCEPT.md §7 item 10).
     */
    public Set<String> pairedSkis() {
        return currentPeerSkis();
    }

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
