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
package org.openhab.binding.boschthermotechnology.internal.handler.support;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;

/**
 * Functional interface matching {@code BaseThingHandler.updateStatus(ThingStatus,
 * ThingStatusDetail, String)}. A handler passes a method reference to its own (inherited,
 * protected) {@code updateStatus} method - legal because the method reference is formed from
 * within the handler's own class body, which has access to the protected member - so
 * {@code ResourcePollingSupport} can report status changes without depending on
 * {@code BaseThingHandler}/{@code BaseBridgeHandler} directly. See ADR-006.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
@FunctionalInterface
public interface ThingStatusUpdater {

    void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description);
}
