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
package org.openhab.binding.sonyprojector.internal.configuration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.sonyprojector.internal.handler.SonyProjectorHandler;

/**
 * The {@link SonyProjectorSerialConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Laurent Garnier - Initial contribution
 */
@NonNullByDefault
public class SonyProjectorSerialConfiguration {
    public String port = "";
    public String model = SonyProjectorHandler.DEFAULT_MODEL.getName();
}
