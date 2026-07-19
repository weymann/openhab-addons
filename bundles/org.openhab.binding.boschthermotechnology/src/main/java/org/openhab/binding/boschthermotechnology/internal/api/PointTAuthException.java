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
 * The {@link PointTAuthException} is thrown when the PointT API responds with HTTP 401 or 403.
 * Per ADR-002 / {@code buderus-reverse.md} section 2 step 8, this signals that the access token
 * has expired (or was rejected) and a refresh must be attempted before retrying; a second
 * occurrence right after a refresh means the refresh token itself is no longer valid and the
 * user must repeat the manual login.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class PointTAuthException extends PointTApiException {

    private static final long serialVersionUID = 1L;

    public PointTAuthException(String message) {
        super(message);
    }
}
