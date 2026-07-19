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

import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_AUTH_URL;
import static org.openhab.binding.boschthermotechnology.internal.BoschThermotechnologyBindingConstants.CONFIG_PASTE_REDIRECT_URL;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient;
import org.openhab.binding.boschthermotechnology.internal.api.PointTApiException;
import org.openhab.binding.boschthermotechnology.internal.api.PointTAuthException;
import org.openhab.binding.boschthermotechnology.internal.api.SingleKeyIdAuthClient;
import org.openhab.binding.boschthermotechnology.internal.dto.TokenResponseDto;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.storage.Storage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AccountBridgeHandler} owns the SingleKey ID login for one Bosch/Buderus account: it
 * starts the PKCE authorization flow, accepts the manually pasted redirect URL to complete it,
 * keeps the access token fresh, and hands out a ready-to-use {@link PointTApiClient} to child
 * {@code gateway} things. See ADR-002 for the full flow and its rationale.
 *
 * <p>
 * The refresh token is persisted via openHAB core's {@link Storage} (backed by
 * {@code StorageService}, one instance per bridge) rather than as a plain {@code Thing} property,
 * so it survives an openHAB restart without requiring the user to log in again while staying out
 * of the regular Thing configuration/property JSON that gets dumped in support bundles and REST
 * responses.
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AccountBridgeHandler extends BaseBridgeHandler {

    private static final String STORAGE_KEY_REFRESH_TOKEN = "refreshToken";
    private static final Duration TOKEN_EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

    private final Logger logger = LoggerFactory.getLogger(AccountBridgeHandler.class);
    private final SingleKeyIdAuthClient authClient;
    private final PointTApiClient apiClient;
    private final Storage<String> storage;

    private @Nullable String pendingState;
    private @Nullable String pendingCodeVerifier;
    private @Nullable String accessToken;
    private @Nullable String refreshToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;
    private @Nullable ScheduledFuture<?> refreshFuture;
    private @Nullable Runnable discoveryScanTrigger;

    public AccountBridgeHandler(Bridge bridge, SingleKeyIdAuthClient authClient, PointTApiClient apiClient,
            Storage<String> storage) {
        super(bridge);
        this.authClient = authClient;
        this.apiClient = apiClient;
        this.storage = storage;
    }

    /**
     * Wires up the callback that triggers a {@code GatewayDiscoveryService} scan. Set once by
     * {@code BoschThermotechnologyHandlerFactory} right after construction, since this handler does
     * not otherwise know about the discovery service registered for it.
     */
    public void setDiscoveryScanTrigger(Runnable discoveryScanTrigger) {
        this.discoveryScanTrigger = discoveryScanTrigger;
    }

    /**
     * Runs the discovery scan trigger (if one was set) asynchronously, so a slow or failing scan
     * can never delay or break the bridge's own authorization/refresh flow that called this.
     */
    private void triggerDiscoveryScan() {
        Runnable trigger = discoveryScanTrigger;
        if (trigger != null) {
            scheduler.execute(trigger);
        }
    }

    @Override
    public void initialize() {
        String storedRefreshToken = storage.get(STORAGE_KEY_REFRESH_TOKEN);
        updateStatus(ThingStatus.UNKNOWN);

        if (storedRefreshToken != null && !storedRefreshToken.isBlank()) {
            this.refreshToken = storedRefreshToken;
            scheduler.execute(this::refreshAndGoOnline);
        } else {
            scheduler.execute(this::startNewAuthorization);
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> future = refreshFuture;
        if (future != null) {
            future.cancel(true);
        }
        refreshFuture = null;
    }

    @Override
    public void handleRemoval() {
        // Do not leave a valid refresh token behind once the bridge itself is deleted.
        storage.remove(STORAGE_KEY_REFRESH_TOKEN);
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // The account bridge currently has no channels of its own - authorization is driven
        // entirely through configuration updates (see handleConfigurationUpdate).
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Object pastedUrl = configurationParameters.get(CONFIG_PASTE_REDIRECT_URL);
        Map<String, Object> sanitizedParameters = new HashMap<>(configurationParameters);
        sanitizedParameters.remove(CONFIG_PASTE_REDIRECT_URL);

        if (pastedUrl instanceof String url && !url.isBlank()) {
            try {
                completeAuthorization(url);
            } catch (PointTApiException e) {
                logger.warn("Could not complete SingleKey ID authorization from pasted URL: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            }
        }

        super.handleConfigurationUpdate(sanitizedParameters);
    }

    /**
     * @return a ready-to-use API client for calling the PointT API.
     */
    public PointTApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Forces the next call to {@link #getValidAccessToken()} to refresh, even if the cached token
     * has not reached its expected expiry yet. Intended for a {@code GatewayHandler} to call after
     * the PointT API itself rejected a token that the clock still considered valid.
     */
    public synchronized void invalidateAccessToken() {
        accessTokenExpiresAt = Instant.EPOCH;
    }

    /**
     * Returns a currently valid access token, refreshing it first if it is expired or about to
     * expire. Intended to be called by child {@code GatewayHandler}s before every PointT API
     * request.
     *
     * @throws PointTAuthException if there is no valid refresh token, or refreshing failed because
     *             the refresh token itself was rejected - the user must repeat the manual login
     * @throws PointTApiException on any other communication failure while refreshing
     */
    public synchronized String getValidAccessToken() throws PointTApiException {
        String currentAccessToken = accessToken;
        if (currentAccessToken != null
                && Instant.now().isBefore(accessTokenExpiresAt.minus(TOKEN_EXPIRY_SAFETY_MARGIN))) {
            return currentAccessToken;
        }
        return doRefresh();
    }

    private void startNewAuthorization() {
        String state = authClient.generateState();
        String codeVerifier = authClient.generateCodeVerifier();
        this.pendingState = state;
        this.pendingCodeVerifier = codeVerifier;

        try {
            String codeChallenge = authClient.deriveCodeChallenge(codeVerifier);
            String authorizationUrl = authClient.buildAuthorizationUrl(state, codeChallenge);

            // Auto-populate the "authUrl" config parameter so the user finds the login URL right in
            // the Thing configuration form of any UI, without digging through logs, properties, or
            // the status message. Cleared again in completeAuthorization() once login succeeds.
            setAuthUrlConfig(authorizationUrl);
            logger.debug("Bosch Thermotechnology account '{}' is not authorized yet, see the '{}' config parameter",
                    thing.getUID(), CONFIG_AUTH_URL);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Not yet authorized - open the URL in the '" + CONFIG_AUTH_URL + "' configuration parameter in "
                            + "a browser, log in, then paste the resulting redirect URL into the '"
                            + CONFIG_PASTE_REDIRECT_URL + "' configuration parameter.");
        } catch (PointTApiException e) {
            logger.warn("Could not prepare SingleKey ID authorization: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private void setAuthUrlConfig(String authorizationUrl) {
        Configuration configuration = editConfiguration();
        configuration.put(CONFIG_AUTH_URL, authorizationUrl);
        updateConfiguration(configuration);
    }

    private void completeAuthorization(String pastedRedirectUrl) throws PointTApiException {
        Map<String, String> queryParams = parseQuery(pastedRedirectUrl);
        String code = queryParams.get("code");
        String state = queryParams.get("state");
        if (code == null || state == null) {
            throw new PointTApiException("Pasted URL is missing the 'code' or 'state' query parameter");
        }

        String expectedState = pendingState;
        String codeVerifier = pendingCodeVerifier;
        if (expectedState == null || codeVerifier == null) {
            throw new PointTApiException("No authorization attempt is in progress - remove and re-add the bridge");
        }
        if (!expectedState.equals(state)) {
            throw new PointTApiException("State mismatch on redirect URL - possible CSRF, aborting login");
        }

        TokenResponseDto token = authClient.exchangeAuthorizationCode(code, codeVerifier);
        storeTokens(token);
        pendingState = null;
        pendingCodeVerifier = null;
        setAuthUrlConfig("");
        updateStatus(ThingStatus.ONLINE);
        triggerDiscoveryScan();
        scheduleProactiveRefresh();
    }

    private void refreshAndGoOnline() {
        try {
            doRefresh();
            updateStatus(ThingStatus.ONLINE);
            triggerDiscoveryScan();
            scheduleProactiveRefresh();
        } catch (PointTAuthException e) {
            logger.warn("Stored refresh token was rejected, a new login is required: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Refresh token is no longer valid - remove and re-add the bridge to log in again");
        } catch (PointTApiException e) {
            logger.debug("Could not refresh access token during initialization: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private synchronized String doRefresh() throws PointTApiException {
        String currentRefreshToken = refreshToken;
        if (currentRefreshToken == null) {
            throw new PointTAuthException("No refresh token available - authorization has not been completed yet");
        }
        TokenResponseDto token = authClient.refreshAccessToken(currentRefreshToken);
        storeTokens(token);
        String newAccessToken = accessToken;
        if (newAccessToken == null) {
            // Not expected: storeTokens() always sets it when the token endpoint call succeeded.
            throw new PointTApiException("Token refresh succeeded but no access token was returned");
        }
        return newAccessToken;
    }

    private synchronized void storeTokens(TokenResponseDto token) {
        this.accessToken = token.accessToken;
        String newRefreshToken = token.refreshToken;
        if (newRefreshToken != null) {
            this.refreshToken = newRefreshToken;
            storage.put(STORAGE_KEY_REFRESH_TOKEN, newRefreshToken);
        }
        this.accessTokenExpiresAt = Instant.now().plusSeconds(token.expiresInSeconds);
    }

    private void scheduleProactiveRefresh() {
        ScheduledFuture<?> previousFuture = refreshFuture;
        if (previousFuture != null) {
            previousFuture.cancel(false);
        }
        long delaySeconds = Duration.between(Instant.now(), accessTokenExpiresAt.minus(TOKEN_EXPIRY_SAFETY_MARGIN))
                .getSeconds();
        refreshFuture = scheduler.schedule(this::proactiveRefresh, Math.max(delaySeconds, 0), TimeUnit.SECONDS);
    }

    private void proactiveRefresh() {
        try {
            doRefresh();
            scheduleProactiveRefresh();
        } catch (PointTAuthException e) {
            logger.warn("Proactive token refresh failed, a new login is required: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Refresh token is no longer valid - remove and re-add the bridge to log in again");
        } catch (PointTApiException e) {
            logger.debug("Proactive token refresh failed, will retry on next API call: {}", e.getMessage());
        }
    }

    private Map<String, String> parseQuery(String url) throws PointTApiException {
        String rawQuery;
        try {
            rawQuery = URI.create(url).getRawQuery();
        } catch (IllegalArgumentException e) {
            throw new PointTApiException("Pasted value is not a valid URL", e);
        }
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new PointTApiException("Pasted URL has no query parameters");
        }

        Map<String, String> result = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separatorIndex = pair.indexOf('=');
            if (separatorIndex < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separatorIndex), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separatorIndex + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
