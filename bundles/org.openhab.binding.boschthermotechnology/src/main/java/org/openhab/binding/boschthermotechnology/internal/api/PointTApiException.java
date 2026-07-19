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
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PointTApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public PointTApiException(String message) {
        super(message);
    }

    public PointTApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
