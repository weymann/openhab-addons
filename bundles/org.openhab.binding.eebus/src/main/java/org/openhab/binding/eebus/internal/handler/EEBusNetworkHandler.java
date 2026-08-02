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
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;

/**
 * The {@link EEBusNetworkHandler} is the Bridge handler for the {@code eebus:network} Thing
 * type: a lightweight anchor Bridge with no mandatory configuration, whose only purpose is to
 * act as the parent for real EEBUS devices discovered via mDNS
 * ({@code EEBusMdnsDiscoveryParticipant}, see ADR-003).
 *
 * <p>
 * This Bridge deliberately holds no SHIP/SPINE identity, certificate, port, or connection
 * state - that remains the exclusive responsibility of {@link EEBusHandler}
 * ({@code eebus:service}). Adding an {@code eebus:network} Thing is a one-step, zero-config
 * action so that discovery of real devices works immediately, before any local identity is
 * configured.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusNetworkHandler extends BaseBridgeHandler {

    public EEBusNetworkHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        // No configuration to validate, nothing to connect to - this Bridge only anchors
        // discovered eebus:peer Inbox entries (ADR-003).
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined on this Bridge.
    }
}
