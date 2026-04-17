package com.example.demo.client.keycloak;

import com.example.demo.constant.auth.KeycloakOAuthConstants;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gọi REST Keycloak Admin (token client_credentials, CRUD user, role-mappings).
 */
@Component
@RequiredArgsConstructor
public class KeycloakAdminApiClient {

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.realm:master}")
    private String keycloakRealm;

    @Value("${app.keycloak.admin.client-id:}")
    private String keycloakAdminClientId;

    @Value("${app.keycloak.admin.client-secret:}")
    private String keycloakAdminClientSecret;

    @Value("${app.keycloak.admin.token-realm:}")
    private String keycloakAdminTokenRealm;

    public boolean isAdminClientConfigured() {
        return keycloakAdminClientId != null && !keycloakAdminClientId.isBlank()
                && keycloakAdminClientSecret != null && !keycloakAdminClientSecret.isBlank();
    }

    public String obtainAdminAccessToken() {
        if (!isAdminClientConfigured()) {
            throw new IllegalStateException(
                    "Chua cau hinh client cho Keycloak Admin API. Dat KEYCLOAK_ADMIN_CLIENT_ID/KEYCLOAK_ADMIN_CLIENT_SECRET hoac KEYCLOAK_REMOTE_CLIENT_ID/KEYCLOAK_REMOTE_CLIENT_SECRET."
            );
        }
        String tokenRealm = (keycloakAdminTokenRealm != null && !keycloakAdminTokenRealm.isBlank())
                ? keycloakAdminTokenRealm.trim()
                : normalizeRealm();
        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String tokenUri = baseUrl + "/realms/" + tokenRealm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", KeycloakOAuthConstants.GRANT_TYPE_CLIENT_CREDENTIALS);
        form.add("client_id", keycloakAdminClientId.trim());
        form.add("client_secret", keycloakAdminClientSecret.trim());

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.postForEntity(
                            URI.create(tokenUri),
                            new HttpEntity<>(form, headers),
                            Map.class
                    );
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("access_token") == null) {
                throw new IllegalStateException("Keycloak admin: khong lay duoc access_token (client_credentials)");
            }
            return body.get("access_token").toString();
        } catch (HttpClientErrorException e) {
            String msg = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "Keycloak admin: lay token that bai " + e.getStatusCode() + " " + msg,
                    e);
        }
    }

    /**
     * Dùng khi cần fallback khi token không đủ quyền (lỗi được nuốt → danh sách rỗng).
     */
    public List<Map<String, Object>> listRealmUsersLenient(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        try {
            return listRealmUsersWithHeaders(headers);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    /**
     * Dùng cho /me: nếu gọi admin API thất bại thì caller fallback sang user hiện tại từ JWT.
     */
    public List<Map<String, Object>> listRealmUsersStrict(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return listRealmUsersWithHeaders(headers);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listRealmUsersWithHeaders(HttpHeaders headers) {
        ResponseEntity<List<Map<String, Object>>> response =
                (ResponseEntity<List<Map<String, Object>>>) (ResponseEntity<?>) restTemplate.exchange(
                        URI.create(buildAdminUsersUri()),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        List.class
                );
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body;
    }

    public boolean userExistsByExactUsername(String adminAccessToken, String username) {
        String url = UriComponentsBuilder.fromUriString(buildAdminUsersUri())
                .queryParam("username", username)
                .queryParam("exact", true)
                .build()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<List<Map<String, Object>>> response =
                    (ResponseEntity<List<Map<String, Object>>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(url),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            List.class
                    );
            List<Map<String, Object>> body = response.getBody();
            return body != null && !body.isEmpty();
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                    "Keycloak admin: kiem tra user that bai " + e.getStatusCode() + " "
                            + e.getResponseBodyAsString(StandardCharsets.UTF_8),
                    e);
        }
    }

    public void createPasswordUser(
            String adminAccessToken,
            String email,
            String username,
            String password,
            String firstName,
            String lastName) {
        Map<String, Object> representation = new java.util.LinkedHashMap<>();
        representation.put("username", username);
        representation.put("email", email);
        representation.put("firstName", firstName);
        representation.put("lastName", lastName);
        representation.put("enabled", true);
        representation.put("emailVerified", true);
        representation.put("credentials", List.of(
                Map.of("type", KeycloakOAuthConstants.CREDENTIAL_TYPE_PASSWORD,
                        "value", password, "temporary", false)
        ));

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setBearerAuth(adminAccessToken);
        postHeaders.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    URI.create(buildAdminUsersUri()),
                    new HttpEntity<>(representation, postHeaders),
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                throw new IllegalArgumentException("Email da duoc su dung", e);
            }
            String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "Keycloak admin tao user that bai: " + e.getStatusCode() + " " + body,
                    e);
        }
    }

    public List<Map<String, Object>> fetchRealmCompositeRoles(String bearerToken, String userId) {
        String roleMappingsUri = (keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim())
                + "/admin/realms/"
                + normalizeRealm()
                + "/users/"
                + userId
                + "/role-mappings/realm/composite";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<List<Map<String, Object>>> response =
                    (ResponseEntity<List<Map<String, Object>>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(roleMappingsUri),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            List.class
                    );
            List<Map<String, Object>> roles = response.getBody();
            return roles == null ? List.of() : roles;
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private String buildAdminUsersUri() {
        return (keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim())
                + "/admin/realms/"
                + normalizeRealm()
                + "/users";
    }

    private String normalizeRealm() {
        return keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
    }
}
