package com.example.demo.service.keycloak;

import com.example.demo.constant.KeycloakOAuthConstants;
import com.example.demo.dto.TokenRequest;
import com.example.demo.dto.TokenResponse;
import com.example.demo.util.OidcTokenResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KeycloakPasswordGrantClient {

    private final RestTemplate restTemplate;

    public TokenResponse login(
            TokenRequest request,
            String tokenUri,
            String clientId,
            String clientSecret,
            String sourceLabel,
            String hint401) {
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
            return OidcTokenResponseMapper.fromTokenEndpointBody(response.getBody());
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
        }
    }

    private HttpEntity<MultiValueMap<String, String>> buildEntity(TokenRequest request, String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("grant_type", KeycloakOAuthConstants.CREDENTIAL_TYPE_PASSWORD);
        form.add("scope", KeycloakOAuthConstants.SCOPE_PASSWORD_GRANT);
        form.add("username", request.getUsername().trim());
        form.add("password", request.getPassword());
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        return new HttpEntity<>(form, headers);
    }

    private void validateRequest(TokenRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Thieu username hoac password");
        }
    }
}
