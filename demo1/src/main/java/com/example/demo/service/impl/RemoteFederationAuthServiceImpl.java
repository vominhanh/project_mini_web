package com.example.demo.service.impl;

import at.favre.lib.crypto.bcrypt.BCrypt;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Service
public class RemoteFederationAuthServiceImpl implements RemoteFederationAuthService {
    @Value("${app.keycloak.remote-federation.client-id:demo-fe}")
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

    @Value("${app.keycloak.admin.client-id:}")
    private String keycloakAdminClientId;

    @Value("${app.keycloak.admin.client-secret:}")
    private String keycloakAdminClientSecret;

    @Value("${app.keycloak.admin.token-realm:}")
    private String keycloakAdminTokenRealm;

    @Value("${app.user-storage.jdbc.url:}")
    private String userStorageJdbcUrl;

    @Value("${app.user-storage.jdbc.username:postgres}")
    private String userStorageJdbcUsername;

    @Value("${app.user-storage.jdbc.password:}")
    private String userStorageJdbcPassword;

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
    public TokenResponse register(com.example.demo.controller.dto.RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request khong hop le");
        }

        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";
        String confirmPassword = request.getConfirmPassword() != null ? request.getConfirmPassword() : "";
        String firstName = request.getFirstName() != null ? request.getFirstName().trim() : "";
        String lastName = request.getLastName() != null ? request.getLastName().trim() : "";

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email khong duoc trong");
        }

        if (password.isBlank()) {
            throw new IllegalArgumentException("Mat khau khong duoc trong");
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mat khau khong trung khop");
        }

        if (email.length() > 50) {
            throw new IllegalArgumentException("Email qua dai (toi da 50 ky tu theo cot username trong DB)");
        }

        try {
            ensureUserRegistered(email, password, firstName, lastName);

            TokenRequest loginRequest = new TokenRequest(email, password);
            return login(loginRequest);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Loi dang ky: " + e.getMessage(), e);
        }
    }


    private void ensureUserRegistered(String email, String password, String firstName, String lastName) {
        boolean hasAdmin = keycloakAdminClientId != null && !keycloakAdminClientId.isBlank()
                && keycloakAdminClientSecret != null && !keycloakAdminClientSecret.isBlank();
        if (hasAdmin) {
            createUserViaKeycloakAdminApi(email, password, firstName, lastName);
            return;
        }
        if (userStorageJdbcUrl != null && !userStorageJdbcUrl.isBlank()) {
            createUserViaSharedUserDatabase(email, password, firstName, lastName);
            return;
        }
        throw new IllegalStateException(
                "Chua cau hinh dang ky: dat KEYCLOAK_ADMIN_CLIENT_ID + KEYCLOAK_ADMIN_CLIENT_SECRET, "
                        + "hoac app.user-storage.jdbc.url (Postgres trung Keycloak User Storage).");
    }

    private void createUserViaSharedUserDatabase(String email, String password, String firstName, String lastName) {
        String fn = (firstName != null && !firstName.isBlank()) ? firstName.trim() : "User";
        String ln = (lastName != null && !lastName.isBlank()) ? lastName.trim() : "Keycloak";
        if (fn.length() > 50) {
            fn = fn.substring(0, 50);
        }
        if (ln.length() > 50) {
            ln = ln.substring(0, 50);
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        String insert = "INSERT INTO users (username, email, firstname, lastname, password) VALUES (?, ?, ?, ?, ?)";

        try (Connection c = DriverManager.getConnection(
                userStorageJdbcUrl.trim(),
                userStorageJdbcUsername != null ? userStorageJdbcUsername.trim() : "postgres",
                userStorageJdbcPassword != null ? userStorageJdbcPassword : "")) {
            if (userExistsInSharedDb(c, normalizedEmail)) {
                throw new IllegalArgumentException("Email da duoc su dung");
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, normalizedEmail);
                ps.setString(2, normalizedEmail);
                ps.setString(3, fn);
                ps.setString(4, ln);
                ps.setString(5, hash);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Email da duoc su dung", e);
            }
            throw new IllegalStateException("Khong ghi duoc user vao Postgres user-storage: " + e.getMessage(), e);
        }
    }

    private boolean userExistsInSharedDb(Connection c, String normalizedEmail) throws SQLException {
        String q = "SELECT 1 FROM users WHERE lower(username) = ? OR lower(email) = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, normalizedEmail);
            ps.setString(2, normalizedEmail);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    private void createUserViaKeycloakAdminApi(String email, String password, String firstName, String lastName) {
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String adminToken = obtainKeycloakAdminAccessToken();
        String adminUsersUri = baseUrl + "/admin/realms/" + realm + "/users";

        if (userExistsInKeycloak(adminUsersUri, adminToken, email)) {
            throw new IllegalArgumentException("Email da duoc su dung");
        }

        String fn = (firstName != null && !firstName.isBlank()) ? firstName.trim() : "User";
        String ln = (lastName != null && !lastName.isBlank()) ? lastName.trim() : "Keycloak";

        Map<String, Object> representation = new java.util.LinkedHashMap<>();
        representation.put("username", email);
        representation.put("email", email);
        representation.put("firstName", fn);
        representation.put("lastName", ln);
        representation.put("enabled", true);
        representation.put("emailVerified", true);
        representation.put("credentials", List.of(
                Map.of("type", "password", "value", password, "temporary", false)
        ));

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setBearerAuth(adminToken);
        postHeaders.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    URI.create(adminUsersUri),
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

    private String obtainKeycloakAdminAccessToken() {
        String tokenRealm = (keycloakAdminTokenRealm != null && !keycloakAdminTokenRealm.isBlank())
                ? keycloakAdminTokenRealm.trim()
                : (keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim());
        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String tokenUri = baseUrl + "/realms/" + tokenRealm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
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

    private boolean userExistsInKeycloak(String adminUsersUri, String adminAccessToken, String username) {
        String url = UriComponentsBuilder.fromUriString(adminUsersUri)
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
        form.add("scope", "openid profile email roles offline_access");
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
                .queryParam("scope", "openid profile email roles offline_access")
                .queryParam("prompt", "select_account")
                .queryParam("kc_idp_hint", "google")
                .build()
                .toUriString();
    }

    @Override
    public String getForgotPasswordUrl(String redirectUri) {
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

        // Keycloak expects a tab_id to bind the reset flow to this browser session.
        String tabId = UUID.randomUUID().toString();

        return UriComponentsBuilder.fromUriString(resetCredentialsUri)
                .queryParam("client_id", browserClientId)
                .queryParam("tab_id", tabId)
                .queryParam("redirect_uri", redirectUri.trim())
                .build()
                .toUriString();
    }

    @Override
    public Map<String, Object> resolveView(String accessToken) {
        List<String> roles = TokenRoleResolver.extractRoles(accessToken);
        String effectiveRole = TokenRoleResolver.resolveEffectiveRole(roles);
        boolean isAdmin = "admin".equalsIgnoreCase(effectiveRole);
        return Map.of(
                "effectiveRole", effectiveRole,
                "roles", roles,
                "canViewUsers", isAdmin,
                "homeTitle", isAdmin ? "Trang chu quan tri" : "Trang chu nguoi dung",
                "homeDescription", isAdmin
                        ? "Tai khoan co quyen admin. Co the xem danh sach user."
                        : "Tai khoan user thong thuong. Giao dien don gian."
        );
    }

    @Override
    public List<Map<String, Object>> getAllUsers(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        String adminUsersUri = (keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim())
                + "/admin/realms/"
                + (keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim())
                + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<List<Map<String, Object>>> response =
                    (ResponseEntity<List<Map<String, Object>>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(adminUsersUri),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            List.class
                    );
            List<Map<String, Object>> body = response.getBody();
            if (body == null) {
                return List.of();
            }
            return body.stream().map(this::toSimpleUser).toList();
        } catch (RestClientException ex) {
            Map<String, Object> currentUser = TokenRoleResolver.extractCurrentUser(accessToken);
            if (currentUser.isEmpty()) {
                return List.of();
            }
            return List.of(currentUser);
        }
    }

    @Override
    public Map<String, Object> getInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        Map<String, Object> currentUser = TokenRoleResolver.extractCurrentUser(accessToken);
        Map<String, Object> claims = TokenRoleResolver.extractAllUserClaims(accessToken);

        boolean needUserInfo = isBlank(currentUser.get("email"))
                || isBlank(currentUser.get("name"));

        Map<String, Object> userInfo = needUserInfo ? fetchUserInfo(accessToken) : Map.of();
        if (!userInfo.isEmpty()) {
            if (isBlank(currentUser.get("email"))) {
                String email = stringOf(userInfo.get("email"));
                if (isBlank(email)) {
                    email = stringOf(userInfo.get("preferred_username"));
                }
                currentUser.put("email", email);
            }

            if (isBlank(currentUser.get("name"))) {
                String name = stringOf(userInfo.get("name"));
                if (isBlank(name)) {
                    String given = stringOf(userInfo.get("given_name"));
                    String family = stringOf(userInfo.get("family_name"));
                    String combined = (given + " " + family).trim();
                    if (!isBlank(combined)) {
                        name = combined;
                    }
                }
                if (isBlank(name)) {
                    name = stringOf(userInfo.get("preferred_username"));
                }
                if (isBlank(name)) {
                    name = stringOf(userInfo.get("sub"));
                }
                currentUser.put("name", name);
            }
        }

        return Map.of(
                "currentUser", currentUser,
                "claims", claims,
                "userInfo", userInfo
        );
    }

    private Map<String, Object> fetchUserInfo(String accessToken) {
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

    private String resolveUserInfoUri() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            // issuerUri thường có dạng http://host/realms/{realm}
            return issuerUri.trim() + "/protocol/openid-connect/userinfo";
        }

        String baseUrl = keycloakAuthServerUrl == null ? "" : keycloakAuthServerUrl.trim();
        String realm = keycloakRealm == null || keycloakRealm.isBlank() ? "master" : keycloakRealm.trim();
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        return value.toString().trim().isEmpty();
    }

    private TokenResponse mapTokenResponse(Map<String, Object> body) {
        Object accessToken = body == null ? null : body.get("access_token");
        Object refreshToken = body == null ? null : body.get("refresh_token");
        Object expiresIn = body == null ? null : body.get("expires_in");
        Object tokenType = body == null ? null : body.get("token_type");
        String token = accessToken == null ? "" : accessToken.toString();
        List<String> roles = TokenRoleResolver.extractRoles(token);

        Map<String, Object> currentUser = TokenRoleResolver.extractCurrentUser(token);
        String email = currentUser.get("email") == null ? "" : currentUser.get("email").toString();
        String name = currentUser.get("name") == null ? "" : currentUser.get("name").toString();
        String username = currentUser.get("username") == null ? "" : currentUser.get("username").toString();

        return new TokenResponse(
                token,
                refreshToken == null ? null : refreshToken.toString(),
                expiresIn == null ? 0 : Integer.parseInt(expiresIn.toString()),
                tokenType == null ? "Bearer" : tokenType.toString(),
                TokenRoleResolver.resolveEffectiveRole(roles),
                roles
        );
    }

    private Map<String, Object> toSimpleUser(Map<String, Object> user) {
        return Map.of(
                "id", stringOf(user.get("id")),
                "email", stringOf(user.get("email")),
                "username", stringOf(user.get("username")),
                "name", (stringOf(user.get("firstName")) + " " + stringOf(user.get("lastName"))).trim(),
                "enabled", user.get("enabled") == null ? "" : user.get("enabled").toString()
        );
    }

    private String stringOf(Object value) {
        return value == null ? "" : value.toString();
    }
}
