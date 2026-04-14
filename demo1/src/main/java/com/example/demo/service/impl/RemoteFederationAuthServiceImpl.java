package com.example.demo.service.impl;

import com.example.demo.constant.auth.UserRegistrationConstants;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.TokenRequest;
import com.example.demo.dto.response.TokenResponse;
import com.example.demo.service.RemoteFederationAuthService;
import com.example.demo.client.keycloak.KeycloakAdminApiClient;
import com.example.demo.client.keycloak.KeycloakBrowserOAuthClient;
import com.example.demo.client.keycloak.KeycloakPasswordGrantClient;
import com.example.demo.service.strategy.auth.UserRegistrationCoordinator;
import com.example.demo.util.TokenRoleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemoteFederationAuthServiceImpl implements RemoteFederationAuthService {

    private static final String LOGIN_HINT =
            "Kiem tra client_id/client_secret (client_not_found, client disabled hoac direct access grants tat); neu client hop le moi kiem tra username/password";

    @Value("${app.keycloak.remote-federation.client-id:demo-fe}")
    private String remoteClientId;

    @Value("${app.keycloak.remote-federation.client-secret:${KEYCLOAK_CLIENT_SECRET:}}")
    private String remoteClientSecret;

    @Value("${app.keycloak.remote-federation.token-uri:}")
    private String remoteTokenUriOverride;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/master}")
    private String issuerUri;

    private final KeycloakPasswordGrantClient keycloakPasswordGrantClient;
    private final KeycloakAdminApiClient keycloakAdminApiClient;
    private final KeycloakBrowserOAuthClient keycloakBrowserOAuthClient;
    private final UserRegistrationCoordinator userRegistrationCoordinator;

    @Override
    public TokenResponse login(TokenRequest request) {
        String tokenUri = (remoteTokenUriOverride != null && !remoteTokenUriOverride.isBlank())
                ? remoteTokenUriOverride
                : issuerUri + "/protocol/openid-connect/token";

        return keycloakPasswordGrantClient.login(
                request,
                tokenUri,
                remoteClientId,
                remoteClientSecret,
                "remote-federation",
                LOGIN_HINT
        );
    }

    @Override
    public TokenResponse register(RegisterRequest request) {
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

        if (email.length() > UserRegistrationConstants.MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Email qua dai (toi da " + UserRegistrationConstants.MAX_EMAIL_LENGTH
                            + " ky tu theo cot username trong DB)");
        }

        try {
            userRegistrationCoordinator.ensureUserRegistered(email, password, firstName, lastName);
            return login(new TokenRequest(email, password));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Loi dang ky: " + e.getMessage(), e);
        }
    }

    @Override
    public TokenResponse exchangeGoogleCode(String code, String redirectUri) {
        return keycloakBrowserOAuthClient.exchangeAuthorizationCode(code, redirectUri);
    }

    @Override
    public String getGoogleAuthUrl(String redirectUri) {
        return keycloakBrowserOAuthClient.buildGoogleAuthUrl(redirectUri);
    }

    @Override
    public String getResetPasswordUrl(String redirectUri) {
        return keycloakBrowserOAuthClient.buildResetPasswordUrl(redirectUri);
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
        try {
            List<Map<String, Object>> body = keycloakAdminApiClient.listRealmUsersStrict(accessToken);
            return body.stream().map(user -> toSimpleUser(user, accessToken)).toList();
        } catch (RestClientException ex) {
            Map<String, Object> currentUser = TokenRoleResolver.extractCurrentUser(accessToken);
            if (currentUser.isEmpty()) {
                return List.of();
            }
            return List.of(toSimpleUser(currentUser, accessToken));
        }
    }

    @Override
    public List<Map<String, Object>> getReportUsers(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            List<Map<String, Object>> users = keycloakAdminApiClient.listRealmUsersLenient(accessToken);
            if (!users.isEmpty()) {
                return users.stream().map(user -> toSimpleUser(user, accessToken)).toList();
            }
        }
        if (keycloakAdminApiClient.isAdminClientConfigured()) {
            String adminToken = keycloakAdminApiClient.obtainAdminAccessToken();
            List<Map<String, Object>> users = keycloakAdminApiClient.listRealmUsersLenient(adminToken);
            if (!users.isEmpty()) {
                return users.stream().map(user -> toSimpleUser(user, adminToken)).toList();
            }
        }

        throw new IllegalStateException(
                "Khong lay duoc danh sach user cho report. Can token co quyen admin Keycloak hoac cau hinh KEYCLOAK_ADMIN_CLIENT_ID/KEYCLOAK_ADMIN_CLIENT_SECRET."
        );
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

        Map<String, Object> userInfo = needUserInfo ? keycloakBrowserOAuthClient.fetchUserInfo(accessToken) : Map.of();
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

    private Map<String, Object> toSimpleUser(Map<String, Object> user, String accessToken) {
        Map<String, Object> simple = new LinkedHashMap<>();
        simple.put("id", stringOf(user.get("id")));
        simple.put("email", stringOf(user.get("email")));
        simple.put("username", stringOf(user.get("username")));
        simple.put("name", (stringOf(user.get("firstName")) + " " + stringOf(user.get("lastName"))).trim());
        simple.put("role", resolveUserRole(user, accessToken));
        simple.put("enabled", user.get("enabled") == null ? "" : user.get("enabled").toString());
        return simple;
    }

    private String resolveUserRole(Map<String, Object> user, String accessToken) {
        String roleFromPayload = firstNonBlank(
                stringOf(user.get("role")),
                stringOf(user.get("roles")),
                stringOf(user.get("realmRoles")));
        if (!roleFromPayload.isBlank()) {
            return roleFromPayload;
        }

        String userId = stringOf(user.get("id"));
        if (userId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return "";
        }

        List<Map<String, Object>> roles = keycloakAdminApiClient.fetchRealmCompositeRoles(accessToken, userId);
        if (roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .map(role -> stringOf(role.get("name")))
                .filter(role -> !role.isBlank())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        return value.toString().trim().isEmpty();
    }

    private String stringOf(Object value) {
        return value == null ? "" : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
