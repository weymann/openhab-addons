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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.eebus.internal.config.EEBusPeerConfiguration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusPeerHandler} represents exactly one paired remote EEBUS device
 * ({@code eebus:peer} Thing), identified by its SKI.
 *
 * <p>
 * Pairing itself is handled by the parent {@link EEBusHandler} (Bridge): this Thing's
 * mere existence adds its {@link EEBusPeerConfiguration#ski} to the Bridge's trusted-SKI
 * set. See CONCEPT.md §5.2.
 * </p>
 *
 * <p>
 * <strong>Architecture note (revised 2026-07-30):</strong> Client-role use-case detection
 * ({@code NodeManagement#addUseCaseListener(...)}) does <em>not</em> live here per-peer.
 * SPINE ties {@code addUseCaseListener} to the local {@code CEM} entity's UseCase list
 * (added once via {@code Device.getBuilder()...withUseCases(...)}, exactly like Server-role
 * use cases - verified against jeebus.spine's demo {@code ExampleUseCase}), not to an
 * individual peer. The callback receives all matching {@code UseCasePartner}s across every
 * paired peer at once, so registration and per-peer routing both happen centrally in
 * {@link EEBusHandler#startShipSpine} (see {@code EEBusMpcClientUseCase} for the first
 * implementation, CONCEPT.md §7.3/§8). This Thing/handler stays a thin per-peer status
 * holder; it does not itself talk to jeebus.spine.
 * </p>
 * <p>
 * <strong>Resolved</strong> (CONCEPT.md §7 items 2 and 9): {@code UseCasePartner
 * #getCommunicationAddress()} is confirmed to be an {@code "ip:port"} string, not the SKI.
 * {@link org.openhab.binding.eebus.internal.transport.EEBusMdnsBrowser} (owned by the parent
 * {@link EEBusHandler}, reachable via {@link EEBusHandler#getMdnsBrowser()}) maintains the
 * matching {@code communicationAddress -> SKI} map by browsing {@code _ship._tcp.local.}
 * directly; {@link EEBusHandler#peerThingUidForSki} completes the chain to this Thing's UID.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusPeerHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EEBusPeerHandler.class);

    private @Nullable EEBusPeerConfiguration config;

    public EEBusPeerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        EEBusPeerConfiguration cfg = getConfigAs(EEBusPeerConfiguration.class);
        this.config = cfg;

        if (cfg.ski.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "ski is required");
            return;
        }

        // Adding this Thing already pairs it - see EEBusHandler#childHandlerInitialized(),
        // which recomputes the Bridge's trusted-SKI set. Per-peer online/offline detection
        // beyond following the Bridge's status is still a placeholder. Use-case detection
        // itself is wired centrally in EEBusHandler#startShipSpine, not here - see class
        // javadoc "Architecture note".
        logger.debug("EEBus peer '{}' configured with SKI {}", thing.getUID(), cfg.ski);

        applyBridgeStatus();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        applyBridgeStatus();
    }

    private void applyBridgeStatus() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not online");
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined yet - see class javadoc.
    }
}
