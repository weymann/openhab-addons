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
package org.openhab.binding.boschthermotechnology.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PointTApiException} is thrown for any non-recoverable failure while communicating
 * with the PointT API (network errors, unexpected HTTP status, malformed responses). It is a
 * checked exception because callers (thing handlers) must always decide how to map it to a
 * {@code ThingStatus} - it is never safe to silently ignore.
 *
 * <p>
 * Since ADR-006, the HTTP status (where known) is carried along via {@link #getHttpStatus()} so
 * that {@code ChildThingDiscoveryService}'s existence probes ({@code tryGetResource} on
 * {@code PointTApiClient}) can distinguish "404, resource genuinely absent" from any other
 * failure, which must not be interpreted as absence.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PointTApiException extends Exception {

    private static final long serialVersionUID = 1L;

    /** {@code -1} if no HTTP response was ever received (e.g. connection/timeout failure). */
    private final int httpStatus;

    public PointTApiException(String message) {
        this(message, -1);
    }

    public PointTApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public PointTApiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * @return the HTTP status code that caused this exception, or {@code -1} if none is known
     *         (e.g. a connection/timeout failure before any response was received).
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
