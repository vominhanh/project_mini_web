package com.example.demo.service.impl;

import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.service.RemoteFederationAuthService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Service
public class RemoteFederationAuthServiceImpl implements RemoteFederationAuthService {
    @Value("${app.keycloak.remote-federation.client-id:${KEYCLOAK_CLIENT_ID:admin-cli}}")
    private String remoteClientId;

    @Value("${app.keycloak.remote-federation.client-secret:${KEYCLOAK_CLIENT_SECRET:}}")
    private String remoteClientSecret;

    @Value("${app.keycloak.remote-federation.token-uri:}")
    private String remoteTokenUriOverride;

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

    private final KeycloakPasswordGrantClient keycloakClient = new KeycloakPasswordGrantClient();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public TokenResponse login(TokenRequest request) {
        String tokenUri = (remoteTokenUriOverride != null && !remoteTokenUriOverride.isBlank())
            ? remoteTokenUriOverride
            : issuerUri + "/protocol/openid-connect/token";

        return keycloakClient.login(
                request,
            tokenUri,
                remoteClientId,
                remoteClientSecret,
                "remote-federation",
                "Kiem tra client_id/client_secret (client_not_found, client disabled hoac direct access grants tat); neu client hop le moi kiem tra username/password"
        );
    }

    @Override
    public TokenResponse exchangeGoogleCode(String code, String redirectUri) {

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
        form.add("grant_type", "authorization_code");
        form.add("client_id",  browserClientId);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (browserClientSecret != null && !browserClientSecret.isBlank()) {
            form.add("client_secret", browserClientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        String browserTokenUri;
        if (browserTokenUriOverride != null && !browserTokenUriOverride.isBlank()) {
            browserTokenUri = browserTokenUriOverride;
        } else {
            String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
            String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
            browserTokenUri = baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        }

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(browserTokenUri),
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );
            return mapTokenResponse(response.getBody());
        } catch (RestClientException ex) {
            throw new IllegalStateException("Khong doi duoc token Google qua Keycloak: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getGoogleAuthUrl(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Redirect URI khong hop le");
        }

        if (browserClientId == null || browserClientId.isBlank()) {
            throw new IllegalStateException("Chua cau hinh app.keycloak.browser.client-id");
        }

        String browserAuthUri;
        if (browserAuthUriOverride != null && !browserAuthUriOverride.isBlank()) {
            browserAuthUri = browserAuthUriOverride;
        } else {
            String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
            String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
            browserAuthUri = baseUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
        }

        return UriComponentsBuilder.fromUriString(browserAuthUri)
                .queryParam("client_id", browserClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email offline_access")
                .queryParam("prompt", "select_account")
                .queryParam("kc_idp_hint", "google")
                .build()
                .toUriString();
    }

    private TokenResponse mapTokenResponse(Map<String, Object> body) {
        Object accessToken = body == null ? null : body.get("access_token");
        Object refreshToken = body == null ? null : body.get("refresh_token");
        Object expiresIn = body == null ? null : body.get("expires_in");
        Object tokenType = body == null ? null : body.get("token_type");
        return new TokenResponse(
                accessToken == null ? "" : accessToken.toString(),
                refreshToken == null ? null : refreshToken.toString(),
                expiresIn == null ? 0 : Integer.parseInt(expiresIn.toString()),
                tokenType == null ? "Bearer" : tokenType.toString()
        );
    }
}
