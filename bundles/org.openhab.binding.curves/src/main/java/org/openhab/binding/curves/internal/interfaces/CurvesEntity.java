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
package org.openhab.binding.curves.internal.interfaces;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface {@link CurvesEntity} as marker interface for all things belonging to Curves
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface CurvesEntity {

    public boolean isOnline();

    public void confirmation(long handle);

    public void dispose();

    public void initialize();
}
