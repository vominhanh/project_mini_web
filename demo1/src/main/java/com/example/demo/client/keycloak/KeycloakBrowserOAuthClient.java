package com.example.demo.client.keycloak;

import com.example.demo.constant.auth.KeycloakOAuthConstants;
import com.example.demo.dto.response.TokenResponse;
import com.example.demo.util.OidcTokenResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Luồng OAuth browser (authorization code, URL auth, reset password, userinfo).
 */
@Component
@RequiredArgsConstructor
public class KeycloakBrowserOAuthClient {

    private final RestTemplate restTemplate;

    @Value("${app.keycloak.browser.client-id:demo-fe}")
    private String browserClientId;

    @Value("${app.keycloak.browser.client-secret:}")
    private String browserClientSecret;

    @Value("${app.keycloak.browser.token-uri:}")
    private String browserTokenUriOverride;

    @Value("${app.keycloak.browser.auth-uri:}")
    private String browserAuthUriOverride;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/master}")
    private String issuerUri;

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.realm:master}")
    private String keycloakRealm;

    public TokenResponse exchangeAuthorizationCode(String code, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code khong hop le");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Redirect URI khong hop le");
        }
        if (browserClientId == null || browserClientId.isBlank()) {
            throw new IllegalStateException("Chua cau hinh app.keycloak.browser.client-id");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", KeycloakOAuthConstants.GRANT_TYPE_AUTHORIZATION_CODE);
        form.add("client_id", browserClientId);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("scope", KeycloakOAuthConstants.SCOPE_BROWSER);
        if (browserClientSecret != null && !browserClientSecret.isBlank()) {
            form.add("client_secret", browserClientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        String browserTokenUri = resolveBrowserTokenUri();

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(browserTokenUri),
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );
            return OidcTokenResponseMapper.fromTokenEndpointBody(response.getBody());
        } catch (RestClientException ex) {
            throw new IllegalStateException("Khong doi duoc token Google qua Keycloak: " + ex.getMessage(), ex);
        }
    }

    public String buildGoogleAuthUrl(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Redirect URI khong hop le");
        }
        if (browserClientId == null || browserClientId.isBlank()) {
            throw new IllegalStateException("Chua cau hinh app.keycloak.browser.client-id");
        }

        String browserAuthUri = resolveBrowserAuthUri();

        return UriComponentsBuilder.fromUriString(browserAuthUri)
                .queryParam("client_id", browserClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", KeycloakOAuthConstants.RESPONSE_TYPE_CODE)
                .queryParam("scope", KeycloakOAuthConstants.SCOPE_BROWSER)
                .queryParam("prompt", KeycloakOAuthConstants.PROMPT_SELECT_ACCOUNT)
                .queryParam("kc_idp_hint", KeycloakOAuthConstants.KC_IDP_HINT_GOOGLE)
                .build()
                .toUriString();
    }

    public String buildResetPasswordUrl(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Redirect URI khong hop le");
        }
        if (browserClientId == null || browserClientId.isBlank()) {
            throw new IllegalStateException("Chua cau hinh app.keycloak.browser.client-id");
        }

        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        String resetCredentialsUri = baseUrl
                + "/realms/" + realm + "/login-actions/reset-credentials";

        // Keycloak cần tab_id để gắn luồng reset với phiên trình duyệt.
        String tabId = UUID.randomUUID().toString();

        return UriComponentsBuilder.fromUriString(resetCredentialsUri)
                .queryParam("client_id", browserClientId)
                .queryParam("tab_id", tabId)
                .queryParam("redirect_uri", redirectUri.trim())
                .build()
                .toUriString();
    }

    public Map<String, Object> fetchUserInfo(String accessToken) {
        String userInfoUri = resolveUserInfoUri();
        if (userInfoUri == null || userInfoUri.isBlank()) {
            return Map.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(userInfoUri),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            Map.class
                    );
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException ex) {
            return Map.of();
        }
    }

    private String resolveBrowserTokenUri() {
        if (browserTokenUriOverride != null && !browserTokenUriOverride.isBlank()) {
            return browserTokenUriOverride;
        }
        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String resolveBrowserAuthUri() {
        if (browserAuthUriOverride != null && !browserAuthUriOverride.isBlank()) {
            return browserAuthUriOverride;
        }
        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
    }

    private String resolveUserInfoUri() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return issuerUri.trim() + "/protocol/openid-connect/userinfo";
        }

        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
    }
}
