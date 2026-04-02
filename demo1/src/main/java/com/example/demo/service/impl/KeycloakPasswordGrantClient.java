package com.example.demo.service.impl;

import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

final class KeycloakPasswordGrantClient {
    private static final String PASSWORD_GRANT = "password";

    private final RestTemplate restTemplate = new RestTemplate();

    TokenResponse login(TokenRequest request, String tokenUri, String clientId, String clientSecret, String sourceLabel, String hint401) {
        validateRequest(request);
       
        if (clientId.isBlank()) {
            throw new IllegalStateException("Chua cau hinh clientId cho " + sourceLabel);
        }
        HttpEntity<MultiValueMap<String, String>> entity = buildEntity(request, clientId, clientSecret);
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(tokenUri), HttpMethod.POST, entity, Map.class
                    );
            return mapTokenResponse(response.getBody());
        } catch (HttpClientErrorException ex) {
            String wwwAuthenticate = ex.getResponseHeaders() == null
                ? null
                : ex.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
            throw new IllegalStateException(
                    "Keycloak " + sourceLabel + " token error: HTTP " + ex.getStatusCode().value()
                            + " (clientId=" + clientId + ", secretProvided=" + (!clientSecret.isBlank()) + ")"
                            + " - tokenUri=" + tokenUri
                            + " - responseBody=" + ex.getResponseBodyAsString()
                    + " - wwwAuthenticate=" + wwwAuthenticate
                            + (ex.getStatusCode().value() == 401 ? " | hint=" + hint401 : "")
            );
            // String body = ex.getResponseBodyAsString();
            // String wwwAuthenticate = ex.getResponseHeaders() == null ? "" : ex.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
            // String hint = ex.getStatusCode().value() == 401 ? " | hint=" + hint401 : "";
            // throw new IllegalStateException(
            //         "Keycloak " + sourceLabel + " token error: HTTP " + ex.getStatusCode().value()
            //                 + " (clientId=" + clientId + ", secretProvided=" + (!clientSecret.isBlank()) + ")"
            //                 + " - tokenUri=" + tokenUri
            //                 + " - responseBody=" + (body == null ? "" : body)
            //                 + " - wwwAuthenticate=" + (wwwAuthenticate == null ? "" : wwwAuthenticate)
            //                 + hint
            // );
        }
    }

    private HttpEntity<MultiValueMap<String, String>> buildEntity(TokenRequest request, String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("grant_type", PASSWORD_GRANT);
        form.add("username", request.getUsername().trim());
        form.add("password", request.getPassword());
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        return new HttpEntity<>(form, headers);
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

    private void validateRequest(TokenRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Thieu username hoac password");
        }
    }

}

