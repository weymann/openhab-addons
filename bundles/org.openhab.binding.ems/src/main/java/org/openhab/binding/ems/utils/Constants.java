/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.ems.utils;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.UnDefType;

import com.google.gson.Gson;

/**
 * Static {@link Constants} definitions
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class Constants {
    public static final Gson GSON = new Gson();
    public static final String UNDEF = UnDefType.UNDEF.toFullString();
}
