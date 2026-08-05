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
 * The {@link EEBusPeerHandler} represents exactly one "real" EEBus device seen on the network
 * ({@code eebus:peer} Thing), identified by its SKI.
 *
 * <p>
 * <strong>Revised (CONCEPT.md §4.5):</strong> split out of the former single {@code eebus:peer}
 * Thing type. This Thing is a child of {@code eebus:network} only, which holds no SHIP/SPINE
 * identity of its own - there is nothing to pair against here. This handler is therefore a thin
 * status holder that simply mirrors its parent Bridge's status; it performs no pairing, no SHIP
 * handshake, and defines no channels. Pairing (trust, dynamically generated channels once
 * use-case detection is implemented) is the responsibility of {@code EEBusOhPeerHandler}
 * (Thing type {@code eebus:oh-peer}, child of {@code eebus:service}) instead.
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

        // No pairing/trust happens here - see class javadoc. This Thing just tracks that a
        // real device with this SKI was seen on the network.
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
