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
package org.openhab.binding.entsoe.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link EntsoEConfiguration} class contains fields mapping thing
 * configuration parameters.
 *
 * @author Jørgen Melhus - Initial contribution
 */
@NonNullByDefault
public class EntsoEConfiguration {
    public String securityToken = "";
    public String area = "";
    public double additionalCost = 0.0;
    public double vat = 0.0;
    public int spotPricesAvailableUtcHour = 11;
    public int historicDays = 1;
}
