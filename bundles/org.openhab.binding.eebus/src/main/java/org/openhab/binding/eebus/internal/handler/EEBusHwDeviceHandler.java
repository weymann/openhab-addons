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
import org.openhab.binding.eebus.internal.config.EEBusHwDeviceConfiguration;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EEBusHwDeviceHandler} represents exactly one "real" EEBus device seen on the network
 * ({@code eebus:hw-device} Thing), identified by its SKI.
 *
 * <p>
 * <strong>Revised (CONCEPT.md §4.5/§7(15), docs/ADR/020-merge-network-peer-things.md):</strong>
 * bridgeless (top-level) Thing - formerly a child of a now-removed {@code eebus:network} anchor
 * Bridge, which held no SHIP/SPINE identity of its own and therefore added nothing beyond being
 * a mandatory, always-ONLINE parent. There is nothing to trust against here. This handler is
 * therefore a thin status holder that goes {@code ONLINE} once configured with a non-blank SKI;
 * it performs no trust handshake, and defines no channels. Trust (and the dynamically generated
 * channels once use-case detection is implemented) is the responsibility of a paired
 * {@code eebus:oh-device} Bridge (the {@code eebus:oh-cs-device} Bridge this used to also
 * apply to is removed, docs/ADR/027-derive-local-use-cases-from-entities.md) and its
 * {@code eebus:oh-entity} children instead.
 * </p>
 *
 * <p>
 * <strong>Renamed (docs/ADR/024-oh-device-oh-entity-rename.md):</strong> this class was
 * {@code EEBusPeerHandler} - renamed to distinguish it clearly from openHAB-managed/simulated
 * concepts, matching the {@code eebus:eebus-peer} -&gt; {@code eebus:hw-device} Thing-type rename.
 * No behavior change beyond the rename.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EEBusHwDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(EEBusHwDeviceHandler.class);

    public EEBusHwDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        EEBusHwDeviceConfiguration cfg = getConfigAs(EEBusHwDeviceConfiguration.class);

        if (cfg.ski.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "ski is required");
            return;
        }

        // No trust and no Bridge happens here - see class javadoc. This Thing just
        // tracks that a real device with this SKI was seen on the network.
        logger.debug("EEBus hardware device '{}' configured with SKI {}", thing.getUID(), cfg.ski);

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are defined yet - see class javadoc.
    }
}
