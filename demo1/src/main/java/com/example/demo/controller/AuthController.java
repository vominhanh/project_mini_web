package com.example.demo.controller;

import com.example.demo.controller.dto.RegisterRequest;
import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.controller.dto.UserPrincipalResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ProviderDbAuthService;
import com.example.demo.service.RemoteFederationAuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProviderDbAuthService providerDbAuthService;
    private final RemoteFederationAuthService remoteFederationAuthService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ProviderDbAuthService providerDbAuthService,
            RemoteFederationAuthService remoteFederationAuthService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.providerDbAuthService = providerDbAuthService;
        this.remoteFederationAuthService = remoteFederationAuthService;
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        return tokenProviderDb(request);
    }

    @PostMapping("/token/provider-db")
    public TokenResponse tokenProviderDb(@RequestBody TokenRequest request) {
        return providerDbAuthService.login(request);
    }

    @PostMapping("/token/remote-federation")
    public TokenResponse tokenRemoteFederation(@RequestBody TokenRequest request) {
        return remoteFederationAuthService.login(request);
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody RegisterRequest request) {
        return registerProviderDb(request);
    }

    @PostMapping("/register/provider-db")
    public Map<String, String> registerProviderDb(@RequestBody RegisterRequest request) {
        validateRegisterRequest(request);

        String email = normalize(request.email());
        String name = normalize(request.name());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email da ton tai trong database local");
        }

        User localUser = new User(email, name, passwordEncoder.encode(request.password()));
        User savedLocalUser = userRepository.save(localUser);

        return Map.of(
                "message", "Dang ky thanh cong vao source Provider DB",
                "source", "provider-db",
                "localUserId", savedLocalUser.getId() == null ? "" : savedLocalUser.getId().toString()
        );
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu đăng ký");
        }
        if (isBlank(request.email())) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Tên không hợp lệ");
        }
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự");
        }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

