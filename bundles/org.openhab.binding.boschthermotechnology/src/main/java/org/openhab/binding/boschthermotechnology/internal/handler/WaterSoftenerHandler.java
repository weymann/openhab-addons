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
package org.openhab.binding.boschthermotechnology.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschthermotechnology.internal.config.SubThingConfiguration;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;

/**
 * The {@link WaterSoftenerHandler} is an intentional placeholder - see ADR-006. The PointT
 * resource path prefix for water softeners was not confirmed by either reverse-engineering source
 * this project is based on ({@code buderus-reverse.md}, {@code myapp-api-analysis.md}); only the
 * device-type string {@code "watersoftener"} is known
 * ({@code PointtConstants.POINT_DEVICE_TYPE_WATER_SOFTENER} in the decompiled MyBuderus app).
 *
 * <p>
 * This thing-type exists (and can be manually added under a {@code gateway} bridge) so the
 * information architecture from ADR-005 is complete, but it has no channels and this handler goes
 * {@link ThingStatus#OFFLINE} with {@link ThingStatusDetail#CONFIGURATION_PENDING} rather than
 * polling anything, until a live gateway confirms the resource path and {@code thing-types.xml}
 * plus this handler are filled in the same way every other child handler already is.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class WaterSoftenerHandler extends BaseThingHandler {

    public WaterSoftenerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        SubThingConfiguration config = getConfigAs(SubThingConfiguration.class);
        if (config.gatewayId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "gatewayId must not be empty");
            return;
        }
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Water softener support is not yet implemented - the PointT resource path for this "
                        + "device class is unconfirmed, see ADR-006");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // No channels are declared for this thing-type yet - nothing to do, see class Javadoc.
    }
}
