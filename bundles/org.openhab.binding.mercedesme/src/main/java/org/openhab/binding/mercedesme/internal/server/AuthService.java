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
package org.openhab.binding.mercedesme.internal.server;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.dto.TokenResponse;
import org.openhab.binding.mercedesme.internal.exception.MercedesMeAuthException;
import org.openhab.binding.mercedesme.internal.utils.Utils;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.storage.Storage;
import org.openhab.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonSyntaxException;

/**
 * {@link AuthService} helpers for token management
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AuthService {
    private static final String CONTENT_TYPE_URL_ENCODED = "application/x-www-form-urlencoded";
    private static final int EXPIRATION_BUFFER = 5;
    private final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private AccessTokenRefreshListener listener;
    private AccountConfiguration config;
    private AccessTokenResponse token = Utils.INVALID_TOKEN;
    private Storage<String> storage;
    private HttpClient httpClient;
    private String identifier;
    private Locale locale;

    public AuthService(AccessTokenRefreshListener atrl, HttpClient hc, AccountConfiguration ac, Locale l,
            Storage<String> store) {
        listener = atrl;
        httpClient = hc;
        config = ac;
        identifier = config.email;
        locale = l;
        storage = store;

        // restore token from persistence if available
        String storedToken = storage.get(identifier);
        if (storedToken != null) {
            // returns INVALID_TOKEN in case of an error
            logger.trace("MB-Auth {} storedToken {}", prefix(), storedToken);
            TokenResponse tokenResponseJson = Utils.GSON.fromJson(storedToken, TokenResponse.class);
            token = decodeToken(tokenResponseJson);
            if (!tokenIsValid()) {
                token = Utils.INVALID_TOKEN;
                storage.remove(identifier);
                logger.trace("MB-Auth {} invalid storedToken {}", prefix(), storedToken);
            }
        } else {
            logger.debug("MB-Auth {} No token stored in persistence", prefix());
        }
    }

    public synchronized String getToken() {
        if (token.isExpired(Instant.now(), EXPIRATION_BUFFER)) {
            if (tokenIsValid()) {
                refreshToken();
            }
        }
        return token.getAccessToken();
    }

    private void refreshToken() {
        logger.trace("MB-Auth {} refreshToken", prefix());
        try {
            String url = Utils.getTokenUrl(config.region);
            Request req = httpClient.POST(url);
            req.header("X-Device-Id", UUID.randomUUID().toString());
            req.header("X-Request-Id", UUID.randomUUID().toString());

            String grantAttribute = "grant_type=refresh_token";
            String refreshTokenAttribute = "refresh_token="
                    + URLEncoder.encode(token.getRefreshToken(), StandardCharsets.UTF_8.toString());
            String content = grantAttribute + "&" + refreshTokenAttribute;
            req.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
            req.content(new StringContentProvider(content));

            ContentResponse cr = req.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            int tokenResponseStatus = cr.getStatus();
            String tokenResponse = cr.getContentAsString();
            if (tokenResponseStatus == 200) {
                storeToken(tokenResponse);
            } else {
                token = Utils.INVALID_TOKEN;
                /**
                 * 1) remove token from storage
                 * 2) listener will be informed about INVALID_TOKEN and bridge will go OFFLINE
                 * 3) user needs to update refreshToken configuration parameter
                 */
                storage.remove(identifier);
                logger.warn("MB-Auth {} Failed to refresh token {} {}", prefix(), tokenResponseStatus, tokenResponse);
            }
            listener.onAccessTokenResponse(token);
        } catch (InterruptedException | TimeoutException | ExecutionException | UnsupportedEncodingException
                | JsonSyntaxException e) {
            logger.info("{} Failed to refresh token {}", prefix(), e.getMessage());
        }
    }

    private void storeToken(String tokenResponse) {
        TokenResponse tokenResponseJson = Utils.GSON.fromJson(tokenResponse, TokenResponse.class);
        if (tokenResponseJson != null) {
            // response doesn't contain creation date time so set it manually
            tokenResponseJson.createdOn = Instant.now().toString();
            // a new refresh token is delivered optional
            // if not set in response take old one
            if (Constants.NOT_SET.equals(tokenResponseJson.refreshToken)) {
                tokenResponseJson.refreshToken = token.getRefreshToken();
            }
            token = decodeToken(tokenResponseJson);
            if (tokenIsValid()) {
                String tokenStore = Utils.GSON.toJson(tokenResponseJson);
                logger.debug("MB-Auth {} token result {}", prefix(), token.toString());
                storage.put(identifier, tokenStore);
            } else {
                token = Utils.INVALID_TOKEN;
                storage.remove(identifier);
                logger.warn("MB-Auth {} Refresh token delivered invalid result {}", prefix(), tokenResponse);
            }
        } else {
            logger.debug("MB-Auth {} token refersh delivered not parsable result {}", prefix(), tokenResponse);
            token = Utils.INVALID_TOKEN;
        }
    }

    private AccessTokenResponse decodeToken(@Nullable TokenResponse tokenJson) {
        if (tokenJson != null) {
            AccessTokenResponse atr = new AccessTokenResponse();
            atr.setCreatedOn(Instant.parse(tokenJson.createdOn));
            atr.setExpiresIn(tokenJson.expiresIn);
            atr.setAccessToken(tokenJson.accessToken);
            if (!Constants.NOT_SET.equals(tokenJson.refreshToken)) {
                atr.setRefreshToken(tokenJson.refreshToken);
            } else {
                // Preserve refresh token if available
                if (!Constants.NOT_SET.equals(token.getRefreshToken())) {
                    atr.setRefreshToken(token.getRefreshToken());
                } else {
                    logger.debug("MB-Auth {} Neither new nor old refresh token available", prefix());
                    return Utils.INVALID_TOKEN;
                }
            }
            atr.setTokenType("Bearer");
            atr.setScope(Constants.AUTH_SCOPE);
            return atr;
        } else {
            logger.debug("MB-Auth {} Neither Token Response is null", prefix());
        }
        return Utils.INVALID_TOKEN;
    }

    public boolean tokenIsValid() {
        return !Constants.NOT_SET.equals(token.getAccessToken()) && !Constants.NOT_SET.equals(token.getRefreshToken());
    }

    public boolean resumeLogin() throws MercedesMeAuthException {
        logger.info("{} Start resume login", prefix());
        /**
         * I need to start an extra client
         * Using common HttpClient causes problems with
         * 2025-07-15 21:48:39.212 [INFO ] [rcedesme.internal.server.AuthService] - [bernd.w@ymann.de] Start resume
         * login
         * 2025-07-15 21:48:39.212 [TRACE] [rcedesme.internal.server.AuthService] - Step 1: Resume headers
         * Accept-Encoding: gzip
         * User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 15_8_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)
         * Version/15.6.6 Mobile/15E148 Safari/604.1
         * Accept-Language: de-DE,de;q=0.9
         * Accept: text/html,application/xhtml+xml,application/xml;q=0.9,;q=0.8
         *
         *
         * 2025-07-15 21:48:39.577 [WARN ] [rcedesme.internal.server.AuthService] - Failed request
         * /as/authorization.oauth2client_id=62778dc4-1de3-44f4-af95-115f06a3a008&code_challenge_method=S256&redirect_uri=rismycar%3A%2F%2Flogin-callback&response_type=code&scope=email+profile+ciam-uid+phone+openid+offline_access&code_challenge=q-xTU0kQx3fLkhAO89qo_4shExS7wa6XVoG1DXAYoZ4
         * - org.eclipse.jetty.http.BadMessageException: 500: Request header too large
         **/

        HttpClient loginHttpClient = new HttpClient(new SslContextFactory.Client());
        try {
            try {
                loginHttpClient.start();
            } catch (Exception e) {
                logger.info("{} Client start failed", prefix());
                return false;
            }
            String codeVerifier = generateCodeVerifier(32);
            String codeChallenge = null;
            try {
                codeChallenge = generateCodeChallenge(codeVerifier);
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
            String baseUrl = Utils.getLoginServer(config.region);
            String resumeUrl = null;

            // Step 1 - get resume parameter
            Fields resumeContent = new Fields();
            resumeContent.put("client_id", Constants.AUTH_CLIENT_ID);
            resumeContent.put("code_challenge_method", "S256");
            resumeContent.put("redirect_uri", Constants.AUTH_REDIRECT_URI);
            resumeContent.put("response_type", "code");
            resumeContent.put("scope", Constants.AUTH_SCOPE);
            resumeContent.put("code_challenge", codeChallenge);

            resumeUrl = baseUrl + "/as/authorization.oauth2?" + FormContentProvider.convert(resumeContent);
            Request resumeRequest = loginHttpClient.newRequest(resumeUrl).followRedirects(true);
            resumeRequest.header(HttpHeader.USER_AGENT, null);
            resumeRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            resumeRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            resumeRequest.header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            logger.trace("Step 1: Resume headers {}", resumeRequest.getHeaders());
            ContentResponse resumeResponse = send(resumeRequest);
            logger.trace("Step 1: Get resume code {} - {}", resumeResponse.getStatus(),
                    resumeResponse.getRequest().getURI());
            if (resumeResponse.getStatus() == 200) {
                String response = resumeResponse.getRequest().getURI().getQuery();
                Map<String, String> params = Utils.getQueryParams(response);
                resumeUrl = params.get("resume");
            }
            if (resumeUrl == null) {
                // abort if not successful
                return false;
            }

            // Step 2 - send user agent
            Request agentRequest = loginHttpClient.POST(baseUrl + "/ciam/auth/ua");
            resumeRequest.header(HttpHeader.USER_AGENT, null);
            agentRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            agentRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            agentRequest.header(HttpHeader.ACCEPT, "*/*");
            agentRequest.header(HttpHeader.ORIGIN, baseUrl);
            agentRequest.header(HttpHeader.CONTENT_TYPE, "application/json");

            JSONObject agentContent = new JSONObject();
            agentContent.put("browserName", "Mobile Safari");
            agentContent.put("browserVersion", "15.6.6");
            agentContent.put("osName", "iOS");
            agentRequest.content(new StringContentProvider(agentContent.toString(), "utf-8"));

            ContentResponse agentResponse = send(agentRequest);
            logger.trace("Step 2: Post Agent {} - {}", agentResponse.getStatus(), agentResponse.getContentAsString());
            if (agentResponse.getStatus() != 200) {
                return false;
            }

            // Step 3 - send user name
            Request userRequest = loginHttpClient.POST(baseUrl + "/ciam/auth/login/user");
            resumeRequest.header(HttpHeader.USER_AGENT, null);
            userRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            userRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            userRequest.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
            userRequest.header(HttpHeader.ORIGIN, baseUrl);
            userRequest.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
            userRequest.header(HttpHeader.CONTENT_TYPE, "application/json");

            JSONObject userContent = new JSONObject();
            userContent.put("username", config.email);
            userRequest.content(new StringContentProvider(userContent.toString(), "utf-8"));

            ContentResponse userResponse = send(userRequest);
            logger.trace("Step 3: Post username {} - {}", userResponse.getStatus(), userResponse.getContentAsString());
            if (userResponse.getStatus() != 200) {
                return false;
            }

            // Step 4 - login
            Request loginRequest = loginHttpClient.POST(baseUrl + "/ciam/auth/login/pass");
            resumeRequest.header(HttpHeader.USER_AGENT, null);
            loginRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            loginRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            loginRequest.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
            loginRequest.header(HttpHeader.ORIGIN, baseUrl);
            loginRequest.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
            loginRequest.header(HttpHeader.CONTENT_TYPE, "application/json");

            String rid = generateCodeVerifier(24);
            JSONObject loginContent = new JSONObject();
            loginContent.put("username", config.email);
            loginContent.put("password", config.password);
            loginContent.put("rememberMe", false);
            loginContent.put("rid", rid);
            loginRequest.content(new StringContentProvider(loginContent.toString(), "utf-8"));

            String preLoginToken = null;
            ContentResponse loginResponse = send(loginRequest);
            String loginResponseString = loginResponse.getContentAsString();
            logger.trace("Step 4: Login {} - {}", loginResponse.getStatus(), loginResponseString);
            if (loginResponse.getStatus() == 200) {
                JSONObject loginResponseJSON = new JSONObject(loginResponseString);
                preLoginToken = loginResponseJSON.optString("token", null);
            }
            if (preLoginToken == null) {
                return false;
            }

            // Step 5 - resume auth
            String code = null;
            MultiMap<@Nullable String> authParams = new MultiMap<>();
            authParams.add("token", preLoginToken);

            Request authRequest = loginHttpClient.POST(baseUrl + resumeUrl).followRedirects(false);
            resumeRequest.header(HttpHeader.USER_AGENT, null);
            authRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            authRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            authRequest.header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            authRequest.header(HttpHeader.ORIGIN, baseUrl);
            authRequest.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
            authRequest.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
            authRequest.content(new StringContentProvider(CONTENT_TYPE_URL_ENCODED,
                    UrlEncoded.encode(authParams, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8));

            ContentResponse authResponse = send(authRequest);
            if (authResponse.getStatus() < 400) {
                String location = authResponse.getHeaders().get(HttpHeader.LOCATION);
                Map<String, String> params = Utils.getQueryParams(URI.create(location).getQuery());
                code = params.get("code");
            } else {
                logger.warn("{} code request {} {}", prefix(), authResponse.getStatus(),
                        authResponse.getContentAsString());
            }
            if (code == null) {
                return false;
            }

            // Step 6 - token request
            Fields tokenParams = new Fields();
            tokenParams.put("client_id", Constants.AUTH_CLIENT_ID);
            tokenParams.put("code", code);
            tokenParams.put("code_verifier", codeVerifier);
            tokenParams.put("grant_type", "authorization_code");
            tokenParams.put("redirect_uri", Constants.AUTH_REDIRECT_URI);

            Request tokenRequest = loginHttpClient.POST(baseUrl + "/as/token.oauth2");
            addBasicHeaders(tokenRequest);
            tokenRequest.content(new FormContentProvider(tokenParams));

            ContentResponse tokenResponse = send(tokenRequest);
            String tokenResponseString = tokenResponse.getContentAsString();
            if (tokenResponse.getStatus() == 200) {
                storeToken(tokenResponseString);
                logger.info("{} Successfully resumed login", prefix());
                return true;
            } else {
                token = Utils.INVALID_TOKEN;
                /**
                 * 1) remove token from storage
                 * 2) listener will be informed about INVALID_TOKEN and bridge will go OFFLINE
                 * 3) user needs to update refreshToken configuration parameter
                 */
                storage.remove(identifier);
                logger.info("Failed resume login {} {}", tokenResponse.getStatus(), tokenResponse.getContentAsString());
            }
        } catch (MercedesMeAuthException e) {
            throw e;
        } finally {
            try {
                loginHttpClient.stop();
            } catch (Exception e) {
                logger.warn("{} Failed to stop HttpClient {}", prefix(), e.getMessage());
            }
        }
        return false;
    }

    private ContentResponse send(Request request) throws MercedesMeAuthException {
        try {
            return request.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Failed request {}{} - {}", request.getPath(), request.getQuery(), e.getMessage());
        }
        throw new MercedesMeAuthException("Request " + request.getPath() + request.getQuery() + "failed");
    }

    private String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String generateCodeVerifier(int size) {
        String verfifierBytes = StringUtils.getRandomAlphanumeric(size);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(verfifierBytes.getBytes());
    }

    public void addBasicHeaders(Request req) {
        req.header("Ris-Os-Name", Constants.RIS_OS_NAME);
        req.header("Ris-Os-Version", Constants.RIS_OS_VERSION);
        req.header("Ris-Sdk-Version", Utils.getRisSDKVersion(config.region));
        req.header("X-Locale", locale.getLanguage() + "-" + locale.getCountry()); // de-DE
        req.header("User-Agent", Utils.getApplication(config.region));
        req.header("X-Applicationname", Utils.getUserAgent(config.region));
        req.header("Ris-Application-Version", Utils.getRisApplicationVersion(config.region));
    }

    private String prefix() {
        return "[" + config.email + "] ";
    }
}
