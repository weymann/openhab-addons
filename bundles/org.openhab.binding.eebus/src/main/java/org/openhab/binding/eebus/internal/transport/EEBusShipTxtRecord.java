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
package org.openhab.binding.eebus.internal.transport;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Parses the SHIP 7.3.2 TXT record fields ({@code ski}, {@code brand}, {@code model},
 * {@code type}) off a resolved {@code _ship._tcp.local.} {@link ServiceInfo}.
 *
 * <p>
 * Shared between {@code EEBusMdnsBrowser} (runtime {@code communicationAddress}-to-SKI
 * bookkeeping for an active {@code eebus:service} session) and
 * {@code EEBusMdnsDiscoveryParticipant} (binding-scoped Inbox population, see ADR-003), so both
 * read exactly the same fields the exact same way instead of maintaining two independent TXT
 * parsers.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public record EEBusShipTxtRecord(String ski, String brand, String model, String type) {

    /**
     * @param info a resolved {@code _ship._tcp.local.} service
     * @return the parsed TXT record, or {@code null} if the service has no usable (non-blank)
     *         {@code ski} field
     */
    public static @Nullable EEBusShipTxtRecord from(ServiceInfo info) {
        String ski = info.getPropertyString("ski");
        if (ski == null || ski.isBlank()) {
            return null;
        }
        return new EEBusShipTxtRecord(ski, nullToEmpty(info.getPropertyString("brand")),
                nullToEmpty(info.getPropertyString("model")), nullToEmpty(info.getPropertyString("type")));
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
