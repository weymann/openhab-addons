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
package org.openhab.binding.boschthermotechnology.internal.dto;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link TokenResponseDto} maps the JSON body returned by
 * {@code POST https://singlekey-id.com/auth/connect/token}, for both the
 * {@code authorization_code} and {@code refresh_token} grant types
 * ({@code buderus-reverse.md}, section 2 steps 4 and 7).
 *
 * <p>
 * This is a plain Gson deserialization target, not a public API type, so it intentionally does
 * not carry {@code @NonNullByDefault} (DTOs are exempt per the openHAB coding guidelines).
 *
 * @author Bernd Weymann - Initial contribution
 */
public class TokenResponseDto {

    @SerializedName("access_token")
    public @Nullable String accessToken;

    @SerializedName("refresh_token")
    public @Nullable String refreshToken;

    @SerializedName("expires_in")
    public long expiresInSeconds;

    @SerializedName("token_type")
    public @Nullable String tokenType;
}
