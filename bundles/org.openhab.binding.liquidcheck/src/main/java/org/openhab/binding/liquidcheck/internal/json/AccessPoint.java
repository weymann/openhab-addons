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
package org.openhab.binding.liquidcheck.internal.json;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link AccessPoint} is used for serializing and deserializing of JSONs.
 * It contains the data for ssid, bssid and the rssi value.
 *
 * @author Marcel Goerentz - Initial contribution
 */
@NonNullByDefault
public class AccessPoint {
    public String ssid = "";
    public String bssid = "";
    public int rssi = 0;
}
