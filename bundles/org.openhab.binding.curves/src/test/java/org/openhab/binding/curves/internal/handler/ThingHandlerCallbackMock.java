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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelGroupTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;

/**
 * The {@link ThingHandlerCallbackMock} Helper Util to read test resource files
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ThingHandlerCallbackMock implements ThingHandlerCallback {

    @Override
    public void stateUpdated(ChannelUID channelUID, State state) {
        // TODO Auto-generated method stub
    }

    @Override
    public void postCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
    }

    @Override
    public void sendTimeSeries(ChannelUID channelUID, TimeSeries timeSeries) {
        // TODO Auto-generated method stub
    }

    @Override
    public void statusUpdated(Thing thing, ThingStatusInfo thingStatus) {
        System.out.println("Status updated for thing: " + thing.getUID() + " with status: " + thingStatus.getStatus()
                + " and details: " + thingStatus.getStatusDetail() + " description " + thingStatus.getDescription());
    }

    @Override
    public void thingUpdated(Thing thing) {
        // TODO Auto-generated method stub
    }

    @Override
    public void validateConfigurationParameters(Thing thing, Map<String, Object> configurationParameters) {
        // TODO Auto-generated method stub
    }

    @Override
    public void validateConfigurationParameters(Channel channel, Map<String, Object> configurationParameters) {
        // TODO Auto-generated method stub
    }

    @Override
    public @Nullable ConfigDescription getConfigDescription(ChannelTypeUID channelTypeUID) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @Nullable ConfigDescription getConfigDescription(ThingTypeUID thingTypeUID) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void configurationUpdated(Thing thing) {
        // TODO Auto-generated method stub
    }

    @Override
    public void migrateThingType(Thing thing, ThingTypeUID thingTypeUID, Configuration configuration) {
        // TODO Auto-generated method stub
    }

    @Override
    public void channelTriggered(Thing thing, ChannelUID channelUID, String event) {
        // TODO Auto-generated method stub
    }

    @Override
    public ChannelBuilder createChannelBuilder(ChannelUID channelUID, ChannelTypeUID channelTypeUID) {
        // TODO Auto-generated method stub
        return ChannelBuilder.create(new ChannelUID(new ThingUID("test:test:test", "testthing"), "testChannel"));
    }

    @Override
    public ChannelBuilder editChannel(Thing thing, ChannelUID channelUID) {
        // TODO Auto-generated method stub
        return ChannelBuilder.create(new ChannelUID(new ThingUID("test:test:test", "testthing"), "testChannel"));
    }

    @Override
    public List<ChannelBuilder> createChannelBuilders(ChannelGroupUID channelGroupUID,
            ChannelGroupTypeUID channelGroupTypeUID) {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public boolean isChannelLinked(ChannelUID channelUID) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @Nullable Bridge getBridge(ThingUID bridgeUID) {
        // TODO Auto-generated method stub
        return null;
    }
}
