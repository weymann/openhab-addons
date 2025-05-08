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
package org.openhab.binding.curves.internal.interfaces;

import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.curves.internal.utils.CurveUtils;
import org.openhab.binding.curves.internal.utils.ItemUtils;
import org.openhab.binding.curves.internal.utils.TimeUtils;

/**
 * Interface {@link Hems} defining the functionality the Energy Management Bridge shall provide
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface Hems {
    public static enum ControllerCommand {
        PV_UPDATE,
        HOUSEHOLD_UPDATE,
        PRICE_UPDATE
    };

    public void registerEntity(CurvesEntity entity);

    public void deregisterEntity(CurvesEntity entity);

    public long handle(CurvesEntity entity, ControllerCommand command);

    public CurveUtils getCurveUtils();

    public ItemUtils getItemUtils();

    public TimeUtils getTimeUtils();

    /**
     * Duration granularity on which the Energy Management System shall calculate on
     *
     * @return granularity as Duration
     */
    public Duration getGranularity();

    public String getUID();
}
