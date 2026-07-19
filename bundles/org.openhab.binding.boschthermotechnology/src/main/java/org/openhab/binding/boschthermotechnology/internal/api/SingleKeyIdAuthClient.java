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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.Oidc.AUTHORIZATION_URL;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.Oidc.CLIENT_ID;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.Oidc.SCOPE;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.Oidc.TOKEN_URL;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschthermotechnology.internal.dto.TokenResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link SingleKeyIdAuthClient} implements the SingleKey ID Authorization Code + PKCE
 * (S256) flow against the public Bosch DashApp client id, mirroring
 * {@code auth.py}/{@code config_flow.py} of the reverse-engineered reference implementation
 * (see {@code buderus-reverse.md}, section 2, and ADR-002).
 *
 * <p>
 * This talks to SingleKey ID directly via {@link HttpClient} and {@link Gson} rather than
 * through openHAB core's {@code OAuthClientService}: this project's exact core version's PKCE
 * support could not be verified offline while implementing this class, and getting the
 * {@code code_verifier} wiring wrong would silently break login. Keeping the whole PKCE exchange
 * in one small, self-contained class - built directly against the documented flow - removes that
 * risk. Revisit this decision if/when core's OAuth2 client SDK's PKCE behavior for this openHAB
 * version is confirmed.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class SingleKeyIdAuthClient {

    /** Fixed, non-configurable redirect URI registered for the DashApp client id. Never resolves. */
    public static final String REDIRECT_URI = "com.buderus.tt.dashtt://app/login";

    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    private static final int CODE_VERIFIER_BYTES = 64;

    private final Logger logger = LoggerFactory.getLogger(SingleKeyIdAuthClient.class);
    private final HttpClient httpClient;
    private final Gson gson;
    private final SecureRandom secureRandom = new SecureRandom();

    public SingleKeyIdAuthClient(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Generates a new PKCE code verifier (64 random bytes, Base64URL, no padding), matching
     * {@code auth.py}.
     */
    public String generateCodeVerifier() {
        byte[] randomBytes = new byte[CODE_VERIFIER_BYTES];
        secureRandom.nextBytes(randomBytes);
        return base64UrlEncode(randomBytes);
    }

    /**
     * Generates a random CSRF state value.
     */
    public String generateState() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64UrlEncode(randomBytes);
    }

    /**
     * Derives the PKCE code challenge (SHA-256, Base64URL, no padding) from a code verifier.
     *
     * @throws PointTApiException if SHA-256 is unavailable (should never happen on a standard JRE)
     */
    public String deriveCodeChallenge(String codeVerifier) throws PointTApiException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return base64UrlEncode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new PointTApiException("SHA-256 not available to derive PKCE code challenge", e);
        }
    }

    /**
     * Builds the authorization URL the user must open in their own browser to log in. The
     * resulting redirect (which does not resolve, see {@link #REDIRECT_URI}) must be pasted back
     * by the user; its {@code code} and {@code state} query parameters are then passed to
     * {@link #exchangeAuthorizationCode(String, String)}.
     */
    public String buildAuthorizationUrl(String state, String codeChallenge) {
        Map<String, String> params = Map.of("client_id", CLIENT_ID, "redirect_uri", REDIRECT_URI, "response_type",
                "code", "scope", SCOPE, "state", state, "code_challenge", codeChallenge, "code_challenge_method",
                "S256", "prompt", "login", "style_id", "tt_bud");
        return AUTHORIZATION_URL + "?" + toQueryString(params);
    }

    /**
     * Exchanges an authorization code (extracted from the pasted redirect URL) for an access and
     * refresh token.
     *
     * @throws PointTAuthException if SingleKey ID rejects the code (invalid, expired, or reused)
     * @throws PointTApiException on any other communication or parsing failure
     */
    public TokenResponseDto exchangeAuthorizationCode(String code, String codeVerifier) throws PointTApiException {
        Map<String, String> form = Map.of("grant_type", "authorization_code", "client_id", CLIENT_ID, "code", code,
                "code_verifier", codeVerifier, "redirect_uri", REDIRECT_URI);
        return postTokenRequest(form);
    }

    /**
     * Refreshes an access token using a previously obtained refresh token.
     *
     * @throws PointTAuthException if the refresh token itself is no longer valid - the caller must
     *             then require the user to repeat the manual login (ADR-002, step 8)
     * @throws PointTApiException on any other communication or parsing failure
     */
    public TokenResponseDto refreshAccessToken(String refreshToken) throws PointTApiException {
        Map<String, String> form = Map.of("grant_type", "refresh_token", "client_id", CLIENT_ID, "refresh_token",
                refreshToken);
        return postTokenRequest(form);
    }

    /**
     * Every call to the token endpoint is logged at TRACE level for first-version debugging, like
     * {@code PointTApiClient}. Unlike that class, the request form and response body here are never
     * logged verbatim - they carry the code verifier, authorization code, refresh token, and access
     * token respectively. Only the grant type, HTTP status, and (on success) the token's declared
     * type/expiry are traced.
     */
    private TokenResponseDto postTokenRequest(Map<String, String> form) throws PointTApiException {
        String body = toQueryString(form);
        logger.trace("--> POST {} grant_type={}", TOKEN_URL, form.get("grant_type"));
        ContentResponse response;
        try {
            response = httpClient.newRequest(TOKEN_URL).method(HttpMethod.POST).content(
                    new StringContentProvider("application/x-www-form-urlencoded", body, StandardCharsets.UTF_8))
                    .timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PointTApiException("Token request to SingleKey ID was interrupted", e);
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
            throw new PointTApiException("Token request to SingleKey ID failed", e);
        }

        int status = response.getStatus();
        logger.trace("<-- POST {} HTTP {} (body withheld - contains tokens)", TOKEN_URL, status);

        if (status == HttpStatus.BAD_REQUEST_400 || status == HttpStatus.UNAUTHORIZED_401
                || status == HttpStatus.FORBIDDEN_403) {
            throw new PointTAuthException(
                    "SingleKey ID rejected the token request (HTTP " + status + "): " + response.getContentAsString());
        }
        if (!HttpStatus.isSuccess(status)) {
            throw new PointTApiException("SingleKey ID token endpoint returned HTTP " + status);
        }

        try {
            TokenResponseDto token = gson.fromJson(response.getContentAsString(), TokenResponseDto.class);
            if (token == null || token.accessToken == null) {
                throw new PointTApiException("SingleKey ID token response did not contain an access token");
            }
            logger.trace("<-- POST {} token_type={} expires_in={}s", TOKEN_URL, token.tokenType,
                    token.expiresInSeconds);
            return token;
        } catch (JsonSyntaxException e) {
            throw new PointTApiException("Could not parse SingleKey ID token response", e);
        }
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toQueryString(Map<String, String> params) {
        return params.entrySet().stream().map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
