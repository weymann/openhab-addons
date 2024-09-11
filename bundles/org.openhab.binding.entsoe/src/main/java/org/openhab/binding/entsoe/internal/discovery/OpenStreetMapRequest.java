/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.entsoe.internal.discovery;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author Miika Jukka - Initial contribution
 *
 */
@NonNullByDefault
public class OpenStreetMapRequest {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final String PAREMETER_ZOOM = "3";

    private final String latitude;
    private final String longitude;

    public OpenStreetMapRequest(String latitude, String longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // @formatter:off
    public String toUrl() {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL)
                .append("?lat=").append(latitude)
                .append("&lon=").append(longitude)
                .append("&zoom=").append(PAREMETER_ZOOM);
        return urlBuilder.toString();
    }

    @Override
    public String toString() {
        return toUrl().toString();
     // @formatter:on
    }
}
