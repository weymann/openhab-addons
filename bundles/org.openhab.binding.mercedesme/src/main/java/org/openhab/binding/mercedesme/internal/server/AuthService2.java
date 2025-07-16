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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
import org.json.JSONObject;
import org.openhab.binding.mercedesme.internal.Constants;
import org.openhab.binding.mercedesme.internal.config.AccountConfiguration;
import org.openhab.binding.mercedesme.internal.utils.Utils;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AuthService} helpers for token management
 *
 * @author Bernd Weymann - Initial contribution
 */
@NonNullByDefault
public class AuthService2 {
    public static final AccessTokenResponse INVALID_TOKEN = new AccessTokenResponse();
    public static final String CONTENT_TYPE_URL_ENCODED = "application/x-www-form-urlencoded";

    private final Logger logger = LoggerFactory.getLogger(AuthService2.class);
    private HttpClient httpClient;
    private AccountConfiguration config;

    public AuthService2(HttpClient hc, AccountConfiguration ac) {
        INVALID_TOKEN.setAccessToken(Constants.NOT_SET);
        INVALID_TOKEN.setRefreshToken(Constants.NOT_SET);
        httpClient = hc;
        config = ac;
    }

    public void login() {
        String codeVerifier = generateCodeVerifier(32);
        // String codeVerifier = "lfejLJVLLDVJLJJJBVEJewjadkjbMVLEMLLVLMÖEVMÖ";
        String sessionId = UUID.randomUUID().toString();
        String resumeUrl = null;

        // Step 1 - get resume parameter
        String baseUrl = Utils.getLoginServer(config.region);

        Fields resumeContent = new Fields();
        // MultiMap<String> resumeContent = new MultiMap<>();
        resumeContent.put("client_id", Constants.AUTH_CLIENT_ID);
        resumeContent.put("code_challenge_method", "S256");
        resumeContent.put("redirect_uri", Constants.AUTH_REDIRECT_URI);
        resumeContent.put("response_type", "code");
        resumeContent.put("scope", Constants.AUTH_SCOPE);
        try {
            String codeChallenge = generateCodeChallenge(codeVerifier);
            logger.warn("Verfifier {} - Challenge {}", codeVerifier, codeChallenge);
            resumeContent.put("code_challenge", codeChallenge);
            FormContentProvider resumeContentProvider = new FormContentProvider(resumeContent);

            logger.warn("{} Login URL: {}", prefix(), FormContentProvider.convert(resumeContent));
            resumeUrl = baseUrl + "/as/authorization.oauth2?" + FormContentProvider.convert(resumeContent);
            Request resumeRequest = httpClient.newRequest(resumeUrl).followRedirects(true);
            resumeRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            resumeRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            resumeRequest.header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            // resumeRequest.content(new StringContentProvider(CONTENT_TYPE_URL_ENCODED,
            // UrlEncoded.encode(resumeContent, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8));
            resumeRequest.content(new FormContentProvider(resumeContent), CONTENT_TYPE_URL_ENCODED);

            ContentResponse cr = resumeRequest.content(new FormContentProvider(resumeContent))
                    .timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            // logger.warn("Content {}", resumeRequest.getContent().);
            logger.warn("Step 1: Get resume code {} - {}", cr.getStatus(), cr.getRequest().getURI());
            if (cr.getStatus() == 200) {
                String response = cr.getRequest().getURI().getQuery();
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), response);
                Map<String, String> params = Utils.getQueryParams(response);
                resumeUrl = params.get("resume");
                logger.warn("Step 1: resume url {}", resumeUrl);
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
                return;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | NoSuchAlgorithmException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
            return;
        }

        // Step 2 - send user agent
        Request agentPost = httpClient.POST(baseUrl + "/ciam/auth/ua");
        agentPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        agentPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        agentPost.header(HttpHeader.ACCEPT, "*/*");
        agentPost.header(HttpHeader.ORIGIN, baseUrl);
        agentPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        JSONObject agentContent = new JSONObject();
        agentContent.put("browserName", "Mobile Safari");
        agentContent.put("browserVersion", "15.6.6");
        agentContent.put("osName", "iOS");

        try {
            agentPost.content(new StringContentProvider(agentContent.toString(), "utf-8"));
            ContentResponse cr = agentPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 2: Post Agent {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 3 - send user name
        Request userPost = httpClient.POST(baseUrl + "/ciam/auth/login/user");
        userPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        userPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        userPost.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
        userPost.header(HttpHeader.ORIGIN, baseUrl);
        userPost.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
        userPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        JSONObject userContent = new JSONObject();
        userContent.put("username", config.email);

        try {
            userPost.content(new StringContentProvider(userContent.toString(), "utf-8"));
            ContentResponse cr = userPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 3: Post username {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 4 - send password
        Request passwordPost = httpClient.POST(baseUrl + "/ciam/auth/login/pass");
        passwordPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        passwordPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        passwordPost.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
        passwordPost.header(HttpHeader.ORIGIN, baseUrl);
        passwordPost.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
        passwordPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        String rid = generateCodeVerifier(24);
        JSONObject passwordContent = new JSONObject();
        passwordContent.put("username", config.email);
        passwordContent.put("password", config.password);
        passwordContent.put("rememberMe", false);
        passwordContent.put("rid", rid);

        String preLoginToken = null;
        try {
            passwordPost.content(new StringContentProvider(passwordContent.toString(), "utf-8"));
            ContentResponse cr = passwordPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 4: Post password {} - {}", cr.getStatus(), response);
            JSONObject loginResponse = new JSONObject(response);
            preLoginToken = loginResponse.optString("token", null);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 5 - resume auth

        String code = null;
        try {
            MultiMap<@Nullable String> resumeAuthParams = new MultiMap<>();
            resumeAuthParams.add("token", preLoginToken);

            Request resumeAuthRequest = httpClient.POST(baseUrl + resumeUrl).followRedirects(false);
            resumeAuthRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            resumeAuthRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            resumeAuthRequest.header(HttpHeader.ACCEPT,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            resumeAuthRequest.header(HttpHeader.ORIGIN, baseUrl);
            resumeAuthRequest.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
            resumeAuthRequest.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
            resumeAuthRequest.content(new StringContentProvider(CONTENT_TYPE_URL_ENCODED,
                    UrlEncoded.encode(resumeAuthParams, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8));

            ContentResponse cr = resumeAuthRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 5: Resume auth {} - {}", cr.getStatus(), response);
            String location = cr.getHeaders().get(HttpHeader.LOCATION);
            logger.warn("Step 5: Resume auth location {}", location);
            logger.warn("Step 5: Resume auth headers {}", cr.getHeaders());
            logger.warn("Step 5: Resume auth request {}", cr.getRequest().getURI());
            Map<String, String> params = Utils.getQueryParams(URI.create(location).getQuery());
            logger.warn("Step 5: Resume auth request {}", params);
            code = params.get("code");
            logger.warn("Step 5: Resume auth code {}", code);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            // e.printStackTrace();
        }

        if (code == null) {
            return;
        }
        // Step 6 - token request
        MultiMap<String> formParams = new MultiMap<>();
        formParams.put("client_id", Constants.AUTH_CLIENT_ID);
        formParams.put("code", code);
        formParams.put("code_verifier", codeVerifier);
        formParams.put("grant_type", "authorization_code");
        formParams.put("redirect_uri", Constants.AUTH_REDIRECT_URI);

        Request tokenRequest = httpClient.POST(baseUrl + "/as/token.oauth2");
        tokenRequest.header("Ris-Os-Name", "android");
        tokenRequest.header("Ris-Os-Version", "10");
        tokenRequest.header("Ris-Sdk-Version", "3.55.0");
        tokenRequest.header("X-Locale", "de-DE"); // de-DE
        tokenRequest.header("User-Agent", "MyCar/2168 CFNetwork/1494.0.7 Darwin/23.4.0");
        tokenRequest.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        tokenRequest.header("X-Trackingid", UUID.randomUUID().toString());
        tokenRequest.header("X-Sessionid", UUID.randomUUID().toString());
        tokenRequest.header("Accept-Language", "de-DE");
        tokenRequest.header("X-Applicationname", "mycar-store-ece");
        tokenRequest.header("Ris-Application-Version", "1.57.0");
        tokenRequest.content(new StringContentProvider(CONTENT_TYPE_URL_ENCODED,
                UrlEncoded.encode(formParams, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8));

        try {
            ContentResponse cr = tokenRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 6: Request headers {}", tokenRequest.getHeaders());
            logger.warn("Step 6: Token {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }
    }

    public void resumeLogin() {
        String codeVerifier = generateCodeVerifier(32);
        String resumeUrl = null;

        // Step 1 - get resume parameter
        String baseUrl = Utils.getLoginServer(config.region);

        try {
            String codeChallenge = generateCodeChallenge(codeVerifier);

            Fields resumeContent = new Fields();
            resumeContent.put("client_id", Constants.AUTH_CLIENT_ID);
            resumeContent.put("code_challenge_method", "S256");
            resumeContent.put("redirect_uri", Constants.AUTH_REDIRECT_URI);
            resumeContent.put("response_type", "code");
            resumeContent.put("scope", Constants.AUTH_SCOPE);
            resumeContent.put("code_challenge", codeChallenge);

            resumeUrl = baseUrl + "/as/authorization.oauth2?" + FormContentProvider.convert(resumeContent);
            Request resumeRequest = httpClient.newRequest(resumeUrl).followRedirects(true);
            resumeRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            resumeRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            resumeRequest.header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            ContentResponse cr = resumeRequest.content(new FormContentProvider(resumeContent))
                    .timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            logger.warn("Step 1: Get resume code {} - {}", cr.getStatus(), cr.getRequest().getURI());
            if (cr.getStatus() == 200) {
                String response = cr.getRequest().getURI().getQuery();
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), response);
                Map<String, String> params = Utils.getQueryParams(response);
                resumeUrl = params.get("resume");
                logger.warn("Step 1: resume url {}", resumeUrl);
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
                return;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | NoSuchAlgorithmException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
            return;
        }

        // Step 2 - send user agent
        Request agentPost = httpClient.POST(baseUrl + "/ciam/auth/ua");
        agentPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        agentPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        agentPost.header(HttpHeader.ACCEPT, "*/*");
        agentPost.header(HttpHeader.ORIGIN, baseUrl);
        agentPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        JSONObject agentContent = new JSONObject();
        agentContent.put("browserName", "Mobile Safari");
        agentContent.put("browserVersion", "15.6.6");
        agentContent.put("osName", "iOS");

        try {
            agentPost.content(new StringContentProvider(agentContent.toString(), "utf-8"));
            ContentResponse cr = agentPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 2: Post Agent {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 3 - send user name
        Request userPost = httpClient.POST(baseUrl + "/ciam/auth/login/user");
        userPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        userPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        userPost.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
        userPost.header(HttpHeader.ORIGIN, baseUrl);
        userPost.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
        userPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        JSONObject userContent = new JSONObject();
        userContent.put("username", config.email);

        try {
            userPost.content(new StringContentProvider(userContent.toString(), "utf-8"));
            ContentResponse cr = userPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 3: Post username {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 4 - send password
        Request passwordPost = httpClient.POST(baseUrl + "/ciam/auth/login/pass");
        passwordPost.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
        passwordPost.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
        passwordPost.header(HttpHeader.ACCEPT, "application/json, text/plain, */*");
        passwordPost.header(HttpHeader.ORIGIN, baseUrl);
        passwordPost.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
        passwordPost.header(HttpHeader.CONTENT_TYPE, "application/json");

        String rid = generateCodeVerifier(24);
        JSONObject passwordContent = new JSONObject();
        passwordContent.put("username", config.email);
        passwordContent.put("password", config.password);
        passwordContent.put("rememberMe", false);
        passwordContent.put("rid", rid);

        String preLoginToken = null;
        try {
            passwordPost.content(new StringContentProvider(passwordContent.toString(), "utf-8"));
            ContentResponse cr = passwordPost.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 4: Post password {} - {}", cr.getStatus(), response);
            JSONObject loginResponse = new JSONObject(response);
            preLoginToken = loginResponse.optString("token", null);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }

        // Step 5 - resume auth

        String code = null;
        try {
            MultiMap<@Nullable String> resumeAuthParams = new MultiMap<>();
            resumeAuthParams.add("token", preLoginToken);

            Request resumeAuthRequest = httpClient.POST(baseUrl + resumeUrl).followRedirects(false);
            resumeAuthRequest.header(HttpHeader.USER_AGENT, Constants.AUTH_USER_AGENT);
            resumeAuthRequest.header(HttpHeader.ACCEPT_LANGUAGE, Constants.AUTH_LANGUAGE);
            resumeAuthRequest.header(HttpHeader.ACCEPT,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            resumeAuthRequest.header(HttpHeader.ORIGIN, baseUrl);
            resumeAuthRequest.header(HttpHeader.REFERER, baseUrl + "/ciam/auth/login");
            resumeAuthRequest.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
            resumeAuthRequest.content(new StringContentProvider(CONTENT_TYPE_URL_ENCODED,
                    UrlEncoded.encode(resumeAuthParams, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8));

            ContentResponse cr = resumeAuthRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            String location = cr.getHeaders().get(HttpHeader.LOCATION);
            logger.warn("Step 5: Resume auth location {}", location);
            logger.warn("Step 5: Resume auth headers {}", cr.getHeaders());
            logger.warn("Step 5: Resume auth request {}", cr.getRequest().getURI());
            Map<String, String> params = Utils.getQueryParams(URI.create(location).getQuery());
            logger.warn("Step 5: Resume auth request {}", params);
            code = params.get("code");
            logger.warn("Step 5: Resume auth code {}", code);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            // e.printStackTrace();
        }

        if (code == null) {
            return;
        }
        // Step 6 - token request
        // MultiMap<String> formParams = new MultiMap<>();
        Fields tokenParams = new Fields();
        tokenParams.put("client_id", Constants.AUTH_CLIENT_ID);
        tokenParams.put("code", code);
        tokenParams.put("code_verifier", codeVerifier);
        tokenParams.put("grant_type", "authorization_code");
        tokenParams.put("redirect_uri", Constants.AUTH_REDIRECT_URI);

        Request tokenRequest = httpClient.POST(baseUrl + "/as/token.oauth2");
        addBasicHeaders(tokenRequest);
        tokenRequest.content(new FormContentProvider(tokenParams));

        try {
            ContentResponse cr = tokenRequest.timeout(Constants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).send();
            String response = cr.getContentAsString();
            logger.warn("Step 6: Request headers {}", tokenRequest.getHeaders());
            logger.warn("Step 6: Token {} - {}", cr.getStatus(), response);
            if (cr.getStatus() == 200) {
                logger.warn("{} Config Request PIN fine {} {}", prefix(), cr.getStatus(), cr.getContentAsString());
            } else {
                logger.warn("{} Failed to request config for pin {} {}", prefix(), cr.getStatus(),
                        cr.getContentAsString());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("{} Failed to request config for pin {}", prefix(), e.getMessage());
        }
    }

    public void addBasicHeaders(Request req) {
        req.header("Ris-Os-Name", Constants.RIS_OS_NAME);
        req.header("Ris-Os-Version", Constants.RIS_OS_VERSION);
        req.header("Ris-Sdk-Version", Utils.getRisSDKVersion(config.region));
        req.header("X-Locale", "de-DE"); // de-DE
        req.header("User-Agent", Utils.getApplication(config.region));
        req.header("X-Applicationname", Utils.getUserAgent(config.region));
        req.header("Ris-Application-Version", Utils.getRisApplicationVersion(config.region));
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

    private String prefix() {
        return "[" + config.email + "] ";
    }
}
