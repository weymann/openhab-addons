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
package org.openhab.binding.dirigera.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ApiMissingException} Exception if no gateway is available
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class ApiMissingException extends RuntimeException {

    private static final long serialVersionUID = 5184571486237890053L;

    public ApiMissingException(String message) {
        super(message);
    }
}