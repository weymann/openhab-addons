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
package org.openhab.binding.brightsky.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Checked exception thrown by {@link BrightSkyApiClient} when an HTTP request
 * fails or the response cannot be parsed.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class BrightSkyApiException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception with a descriptive message.
     *
     * @param message human-readable description of the failure
     */
    public BrightSkyApiException(String message) {
        super(message);
    }

    /**
     * Creates a new exception wrapping a lower-level cause.
     *
     * @param message human-readable description of the failure
     * @param cause the underlying exception
     */
    public BrightSkyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
