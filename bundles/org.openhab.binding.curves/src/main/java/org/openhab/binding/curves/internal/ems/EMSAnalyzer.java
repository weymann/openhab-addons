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
package org.openhab.binding.curves.internal.ems;

import java.time.Instant;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EMSAnalyzer} check for energy shortages and prices
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class EMSAnalyzer {

    private final Logger logger = LoggerFactory.getLogger(EMSAnalyzer.class);

    public EMSAnalyzer(TreeMap<Instant, State> pv, TreeMap<Instant, State> household, TreeMap<Instant, State> pricaes,
            double soc, double batteryCharge) {
    }
}
