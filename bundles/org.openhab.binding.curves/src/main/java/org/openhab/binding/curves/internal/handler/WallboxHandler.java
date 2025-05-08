/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.curves.internal.handler;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.config.WallboxConfiguration;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Wallbox;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WallboxHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class WallboxHandler extends BaseEntityHandler implements Wallbox {

    private final Logger logger = LoggerFactory.getLogger(WallboxHandler.class);

    private WallboxConfiguration config;

    public WallboxHandler(Thing thing) {
        super(thing);
        config = getConfigAs(WallboxConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        config = getConfigAs(WallboxConfiguration.class);
        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
                    control.registerEntity(this);
                    internalUpdateStatus(ThingStatus.ONLINE, null, null);
                } else {
                    internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "@text/solarforecast.plane.status.wrong-handler" + " [\"" + handler + "\"]");
                    return;
                }
            } else {
                internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/solarforecast.plane.status.bridge-handler-not-found");
                return;
            }
        } else {
            internalUpdateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/solarforecast.plane.status.bridge-missing");
            return;
        }
    }

    @Override
    public void dispose() {
        controller().deregisterEntity(this);
        super.dispose();
    }
}
