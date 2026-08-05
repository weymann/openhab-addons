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

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.EEBusBindingConstants;
import org.openhab.binding.eebus.internal.config.EEBusOhPeerConfiguration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusOhPeerHandler} represents exactly one openHAB-managed pairing with a remote
 * EEBUS device ({@code eebus:oh-peer} Thing), identified by its SKI.
 *
 * <p>
 * <strong>Pairing model (revised, docs/ADR/012-pairing-trust-property-and-actions.md /
 * CONCEPT.md §4.6):</strong> configuring this Thing (choosing/entering its
 * {@link EEBusOhPeerConfiguration#ski}) no longer by itself adds it to the parent
 * {@link EEBusHandler} (Bridge)'s trusted-SKI set. Trust is granted explicitly via the
 * {@link EEBusOhPeerActions#pair()} Thing Action (delegating to {@link #pair()} here) and
 * revoked via {@link EEBusOhPeerActions#unpair()} ({@link #unpair()}), without removing this
 * Thing. Pairing state is recorded in the {@value EEBusBindingConstants#PROPERTY_PAIRED} Thing
 * property, which survives openHAB restarts - see {@link #isPaired()}.
 * </p>
 *
 * <p>
 * <strong>Origin (CONCEPT.md §4.5):</strong> split out of the former single {@code eebus:peer}
 * Thing type - this class takes over the pairing/channel/metadata responsibilities previously
 * described for {@code eebus:peer} when configured under {@code eebus:service}. The passive,
 * real-device-record half of that former type now lives in {@link EEBusPeerHandler}
 * ({@code eebus:peer}, child of {@code eebus:network} only).
 * </p>
 *
 * <p>
 * <strong>Architecture note (carried over from the pre-split {@code EEBusPeerHandler}):</strong>
 * Client-role use-case detection ({@code NodeManagement#addUseCaseListener(...)}) does
 * <em>not</em> live here per-peer. SPINE ties {@code addUseCaseListener} to the local
 * {@code CEM} entity's UseCase list (added once via {@code Device.getBuilder()...withUseCases(...)},
 * exactly like Server-role use cases - verified against jeebus.spine's demo
 * {@code ExampleUseCase}), not to an individual peer. The callback receives all matching
 * {@code UseCasePartner}s across every paired peer at once, so registration and per-peer
 * routing both happen centrally in {@link EEBusHandler#startShipSpine} (see
 * {@code EEBusMpcClientUseCase} for the first implementation, CONCEPT.md §7.3/§8). This
 * Thing/handler stays a thin per-peer status holder; it does not itself talk to jeebus.spine.
 * </p>
 * <p>
 * <strong>Resolved</strong> (CONCEPT.md §7 items 2 and 9): {@code UseCasePartner
 * #getCommunicationAddress()} is confirmed to be an {@code "ip:port"} string, not the SKI.
 * {@link org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser} (owned by the parent
 * {@link EEBusHandler}, reachable via {@link EEBusHandler#getMdnsBrowser()}) maintains the
 * matching {@code communicationAddress -> SKI} map by browsing {@code _ship._tcp.local.}
 * directly; {@link EEBusHandler#ohPeerThingUidForSki} completes the chain to this Thing's UID.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusOhPeerHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EEBusOhPeerHandler.class);

    private @Nullable EEBusOhPeerConfiguration config;

    public EEBusOhPeerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        EEBusOhPeerConfiguration cfg = getConfigAs(EEBusOhPeerConfiguration.class);
        this.config = cfg;

        if (cfg.ski.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "ski is required");
            return;
        }

        // Configuring this Thing no longer by itself pairs it - see class javadoc and
        // EEBusHandler#currentPairedOhPeerSkis(). Per-peer online/offline detection beyond
        // following the Bridge's status and this Thing's own pairing property is still a
        // placeholder. Use-case detection itself is wired centrally in
        // EEBusHandler#startShipSpine, not here - see class javadoc "Architecture note".
        logger.debug("EEBus OH peer '{}' configured with SKI {} (paired={})", thing.getUID(), cfg.ski, isPaired());

        applyStatus();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        applyStatus();
    }

    /**
     * @return {@code true} if {@link EEBusBindingConstants#PROPERTY_PAIRED} is currently set on
     *         this Thing, i.e. {@link #pair()} has been invoked and not since undone by
     *         {@link #unpair()}. Backed by a Thing property, so this survives openHAB restarts
     *         without {@link #pair()} needing to be invoked again - see docs/ADR/012-pairing-
     *         trust-property-and-actions.md.
     */
    boolean isPaired() {
        return "true".equals(thing.getProperties().get(EEBusBindingConstants.PROPERTY_PAIRED));
    }

    /**
     * Grants trust: sets {@value EEBusBindingConstants#PROPERTY_PAIRED} on this Thing and asks
     * the parent {@link EEBusHandler} to recompute its trusted-SKI set. Idempotent - invoking
     * this on an already-paired Thing is a harmless no-op (per docs/changes/decouple-oh-peer-
     * config-from-pairing/specs/thing-model/spec.md, Scenario "Invoking pair() on an
     * already-paired Thing is a no-op"). Called from {@link EEBusOhPeerActions#pair()}.
     */
    void pair() {
        updateProperty(EEBusBindingConstants.PROPERTY_PAIRED, "true");
        requestTrustedSkiRecompute();
        applyStatus();
    }

    /**
     * Revokes trust without removing this Thing: clears
     * {@value EEBusBindingConstants#PROPERTY_PAIRED} and asks the parent {@link EEBusHandler} to
     * recompute its trusted-SKI set. This Thing's {@code ski} configuration is left untouched, so
     * it can be re-paired later via {@link #pair()} without re-entering the SKI. Idempotent -
     * invoking this on an already-unpaired Thing is a harmless no-op. Called from
     * {@link EEBusOhPeerActions#unpair()}.
     */
    void unpair() {
        updateProperty(EEBusBindingConstants.PROPERTY_PAIRED, null);
        requestTrustedSkiRecompute();
        applyStatus();
    }

    /**
     * Asks the parent {@link EEBusHandler} Bridge to recompute its trusted-SKI set immediately,
     * without a full Thing dispose/initialize cycle - see {@link EEBusHandler#recomputeTrustedSkis()}'s
     * javadoc for why that method is package-private rather than only reachable via the Bridge's
     * own child-lifecycle callbacks.
     */
    private void requestTrustedSkiRecompute() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return;
        }
        if (bridge.getHandler() instanceof EEBusHandler bridgeHandler) {
            bridgeHandler.recomputeTrustedSkis();
        }
    }

    private void applyStatus() {
        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not online");
            return;
        }
        if (!isPaired()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Configured but not yet paired - invoke the \"Pair\" Thing Action");
            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined yet - see class javadoc.
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(EEBusOhPeerActions.class);
    }
}
