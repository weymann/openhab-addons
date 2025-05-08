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
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.curves.internal.calculator.DataLake;
import org.openhab.binding.curves.internal.exception.ControllerMissingException;
import org.openhab.binding.curves.internal.exception.DataLakeException;
import org.openhab.binding.curves.internal.interfaces.CurvesEntity;
import org.openhab.binding.curves.internal.interfaces.Hems;
import org.openhab.binding.curves.internal.interfaces.Hems.ControllerCommand;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.ItemUtils;
import org.openhab.binding.curves.internal.utils.TimeUtils;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BaseEntityHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public abstract class BaseEntityHandler extends BaseThingHandler implements CurvesEntity {

    private final Logger logger = LoggerFactory.getLogger(BaseEntityHandler.class);

    protected ThingStatus status = ThingStatus.UNINITIALIZED;
    protected TreeMap<Long, ControllerCommand> confirmationMap = new TreeMap<>();
    protected Optional<Hems> controller = Optional.empty();
    protected Optional<DataLake> datalake = Optional.empty();

    public BaseEntityHandler(Thing thing) {
        super(thing);
    }

    @SuppressWarnings({ "unused", "null" })
    @Override
    public void confirmation(long handle) {
        ControllerCommand command = confirmationMap.remove(handle);
        if (command == null) {
            logger.trace("[CURVES_HANDLER] Command {} with handle {} was never requested", command.toString(), handle);
        }
    }

    @Override
    public void initialize() {
        // check bridge
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null) {
                if (handler instanceof Hems control) {
                    controller = Optional.of(control);
                    control.registerEntity(this);
                    datalake = Optional.of(DataLake.getInstance(control.getUID()));
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

    protected Hems controller() {
        if (controller.isPresent()) {
            return controller.get();
        }
        throw new ControllerMissingException("Curves Controller missing");
    }

    protected DataLake datalake() {
        if (datalake.isPresent()) {
            return datalake.get();
        }
        throw new DataLakeException("DataLake missing");
    }

    protected TimeUtils times() {
        return controller().getTimeUtils();
    }

    protected ItemUtils items() {
        return controller().getItemUtils();
    }

    protected CurveUtils curves() {
        return controller().getCurveUtils();
    }

    @Override
    public boolean isOnline() {
        return ThingStatus.ONLINE.equals(status);
    }

    public void internalUpdateStatus(ThingStatus status, @Nullable ThingStatusDetail details,
            @Nullable String description) {
        this.status = status;
        if (details != null) {
            super.updateStatus(status, details, description);
        } else {
            super.updateStatus(status);
        }
    }
}
