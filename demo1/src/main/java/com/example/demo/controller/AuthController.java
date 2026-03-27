package com.example.demo.controller;

import com.example.demo.controller.dto.RegisterRequest;
import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.controller.dto.UserPrincipalResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${app.keycloak.remote-federation.client-id:${KEYCLOAK_CLIENT_ID:demo-client}}")
    private String remoteFederationClientId;

    @Value("${app.keycloak.remote-federation.client-secret:${KEYCLOAK_CLIENT_SECRET:}}")
    private String remoteFederationClientSecret;

    @Value("${app.keycloak.remote-federation.token-uri:}")
    private String remoteFederationTokenUriOverride;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/master}")
    private String issuerUri;

    private String resolveTokenUri(String tokenUriOverride) {
        if (tokenUriOverride != null && !tokenUriOverride.isBlank()) {
            return tokenUriOverride;
        }
        return issuerUri + "/protocol/openid-connect/token";
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        return tokenRemoteFederation(request);
    }

    @PostMapping("/token/provider-db")
    public TokenResponse tokenProviderDb(@RequestBody TokenRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new IllegalArgumentException("Thiếu username hoặc password");
        }

        User user = userRepository.findByEmail(request.username().trim())
                .orElseThrow(() -> new IllegalStateException("Sai tai khoan hoac mat khau"));

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalStateException("Sai tai khoan hoac mat khau");
        }

        return new TokenResponse(
                "LOCAL_AUTH_SUCCESS",
                null,
                0,
                "Local"
        );
    }

    @PostMapping("/token/remote-federation")
    public TokenResponse tokenRemoteFederation(@RequestBody TokenRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new IllegalArgumentException("Thiếu username hoặc password");
        }

        String tokenUri = resolveTokenUri(remoteFederationTokenUriOverride);
        String normalizedClientId = remoteFederationClientId == null ? "" : remoteFederationClientId.trim();
        String normalizedClientSecret = remoteFederationClientSecret == null ? "" : remoteFederationClientSecret.trim();
        if (normalizedClientId.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình remote-federation clientId");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("client_id", normalizedClientId);
        form.add("grant_type", "password");
        form.add("username", request.username().trim());
        form.add("password", request.password());
        if (!normalizedClientSecret.isBlank()) {
            form.add("client_secret", normalizedClientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        ResponseEntity<Map<String, Object>> response;
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> castResponse =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                            URI.create(tokenUri), HttpMethod.POST, entity, Map.class
                    );
            response = castResponse;
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            String body = ex.getResponseBodyAsString();
            String wwwAuthenticate = ex.getResponseHeaders() == null ? "" : ex.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
            String hint = "";
            if (ex.getStatusCode().value() == 401) {
                hint = " | hint=401 o day thuong la sai username/password, user chua duoc Keycloak federation tim thay, "
                        + "hoac client demo-client chua bat Direct Access Grants.";
            }
            throw new IllegalStateException(
                    "Keycloak remote token error: HTTP " + ex.getStatusCode().value()
                            + " (clientId=" + normalizedClientId + ", secretProvided=" + (!normalizedClientSecret.isBlank()) + ")"
                            + " - tokenUri=" + tokenUri
                            + " - responseBody=" + (body == null ? "" : body)
                            + " - wwwAuthenticate=" + (wwwAuthenticate == null ? "" : wwwAuthenticate)
                            + hint
            );
        }

        Object accessToken = response.getBody() == null ? null : response.getBody().get("access_token");
        Object refreshToken = response.getBody() == null ? null : response.getBody().get("refresh_token");
        Object expiresIn = response.getBody() == null ? null : response.getBody().get("expires_in");
        Object tokenType = response.getBody() == null ? null : response.getBody().get("token_type");

        return new TokenResponse(
                accessToken == null ? "" : accessToken.toString(),
                refreshToken == null ? null : refreshToken.toString(),
                expiresIn == null ? 0 : Integer.parseInt(expiresIn.toString()),
                tokenType == null ? "Bearer" : tokenType.toString()
        );
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody RegisterRequest request) {
        return registerProviderDb(request);
    }

    @PostMapping("/register/provider-db")
    public Map<String, String> registerProviderDb(@RequestBody RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu đăng ký");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tên không hợp lệ");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự");
        }

        String email = request.email().trim();
        String name = request.name().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email da ton tai trong database local");
        }

        User localUser = new User(
                email,
                name,
                passwordEncoder.encode(request.password())
        );
        User savedLocalUser = userRepository.save(localUser);

        return Map.of(
                "message", "Dang ky thanh cong vao source Provider DB",
                "source", "provider-db",
                "localUserId", savedLocalUser.getId() == null ? "" : savedLocalUser.getId().toString()
        );
    }

    @GetMapping("/me")
    public UserPrincipalResponse me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException("Chưa có JWT principal");
        }

        String email = (String) jwt.getClaims().getOrDefault("email", "");
        String preferredUsername = (String) jwt.getClaims().getOrDefault("preferred_username", "");

        return new UserPrincipalResponse(
                jwt.getSubject(),
                email,
                preferredUsername,
                jwt.getClaims()
        );
    }
}

