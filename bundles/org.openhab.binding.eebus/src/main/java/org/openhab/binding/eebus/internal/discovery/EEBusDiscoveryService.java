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
package org.openhab.binding.eebus.internal.discovery;

/**
 * Superseded by {@link EEBusMdnsDiscoveryParticipant} (ADR-003,
 * {@code docs/ADR/003-decouple-mdns-discovery-from-bridge.md}): real-device discovery is now
 * binding-scoped instead of {@code eebus:service} Bridge-scoped, so it works before any local
 * SHIP identity is configured.
 *
 * <p>
 * <strong>Pending manual deletion:</strong> this file could not be removed from disk in the
 * session that authored ADR-003 (the working copy's filesystem mount rejected the delete/unlink
 * operation - write access worked, deletion did not). It has been emptied of all logic and its
 * {@code @Component} registration removed, so it registers nothing and has no runtime effect.
 * Please delete this file manually as part of merging change
 * {@code docs/changes/eebus-network-discovery/}; it is intentionally not referenced from
 * anywhere in the codebase anymore.
 * </p>
 *
 * @author Bernd Weymann - Initial contribution
 */
final class EEBusDiscoveryService {

    private EEBusDiscoveryService() {
        // Not instantiable - see class javadoc.
    }
}
