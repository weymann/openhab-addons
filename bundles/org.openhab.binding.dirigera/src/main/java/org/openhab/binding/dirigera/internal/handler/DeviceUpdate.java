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
package org.openhab.binding.dirigera.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link DeviceUpdate} element handled in device update queue
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class DeviceUpdate {
    public enum Action {
        ADD,
        DISPOSE,
        REMOVE,
        LINKS;
    }

    public @Nullable BaseHandler handler;
    public String deviceId;
    public Action action;

    public DeviceUpdate(@Nullable BaseHandler handler, String deviceId, Action action) {
        this.handler = handler;
        this.deviceId = deviceId;
        this.action = action;
    }

    /**
     * Link updates are equal because they are generic, all others false
     *
     * @param other
     * @return
     */
    @Override
    public boolean equals(@Nullable Object other) {
        boolean result = false;
        if (other instanceof DeviceUpdate update) {
            result = this.action.equals(update.action) && this.deviceId.equals(update.deviceId);
            if (result && this.handler != null && update.handler != null) {
                result = handler.equals(update.handler);
            }
        }
        return result;
    }
}