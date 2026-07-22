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
package org.openhab.binding.boschthermotechnology.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;

/**
 * The {@link PointTAccessProvider} is implemented by every bridge handler that a child handler
 * needs API access through: {@code AccountBridgeHandler} (for the {@code gateway} bridge) and
 * {@code GatewayHandler} itself (for the eight child thing-types introduced by ADR-005/ADR-006,
 * which simply delegates to its own parent {@code AccountBridgeHandler} - the access token is
 * account-scoped, a {@code gateway} has none of its own).
 *
 * <p>
 * {@code ResourcePollingSupport} depends only on this interface, not on either concrete handler
 * class, so it works identically whether it is polling the {@code gateway} bridge's own system
 * channels or one of the child things' channels.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public interface PointTAccessProvider {

    /**
     * @return a ready-to-use API client for calling the PointT API.
     */
    PointTApiClient getApiClient();

    /**
     * Returns a currently valid access token, refreshing it first if necessary.
     *
     * @throws PointTAuthException if there is no valid refresh token, or refreshing failed because
     *             the refresh token itself was rejected
     * @throws PointTApiException on any other communication failure while refreshing
     */
    String getValidAccessToken() throws PointTApiException;

    /**
     * Forces the next call to {@link #getValidAccessToken()} to refresh, even if the cached token
     * has not reached its expected expiry yet. Called after the PointT API itself rejected a token
     * that the clock still considered valid.
     */
    void invalidateAccessToken();
}
