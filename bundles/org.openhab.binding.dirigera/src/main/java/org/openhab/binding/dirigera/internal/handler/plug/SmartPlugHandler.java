/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.dirigera.internal.handler.plug;

import static org.openhab.binding.dirigera.internal.Constants.*;

import java.util.Iterator;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONObject;
import org.openhab.binding.dirigera.internal.model.Model;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

/**
 * The {@link SmartPlugHandler} basic DeviceHandler for all devices
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class SmartPlugHandler extends PowerPlugHandler {

    public SmartPlugHandler(Thing thing, Map<String, String> mapping) {
        super(thing, mapping);
        super.setChildHandler(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        // update of values is handled in super class
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);
    }

    @Override
    public void handleUpdate(JSONObject update) {
        // handle reachable flag
        super.handleUpdate(update);
        // now device specific
        if (update.has(Model.ATTRIBUTES)) {
            JSONObject attributes = update.getJSONObject(Model.ATTRIBUTES);
            Iterator<String> attributesIterator = attributes.keys();
            while (attributesIterator.hasNext()) {
                String key = attributesIterator.next();
                String targetChannel = property2ChannelMap.get(key);
                if (targetChannel != null) {
                    switch (targetChannel) {
                        case CHANNEL_POWER:
                            updateState(new ChannelUID(thing.getUID(), targetChannel),
                                    QuantityType.valueOf(attributes.getDouble(key), Units.WATT));
                            break;
                        case CHANNEL_CURRENT:
                            updateState(new ChannelUID(thing.getUID(), targetChannel),
                                    QuantityType.valueOf(attributes.getDouble(key), Units.AMPERE));
                            break;
                        case CHANNEL_POTENTIAL:
                            updateState(new ChannelUID(thing.getUID(), targetChannel),
                                    QuantityType.valueOf(attributes.getDouble(key), Units.VOLT));
                            break;
                        case CHANNEL_ENERGY_TOTAL:
                        case CHANNEL_ENERGY_RESET:
                            updateState(new ChannelUID(thing.getUID(), targetChannel),
                                    QuantityType.valueOf(attributes.getDouble(key), Units.KILOWATT_HOUR));
                    }
                }
            }
        }
    }
}