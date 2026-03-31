package com.example.demo.controller;

import com.example.demo.controller.dto.FederationLookupRequest;
import com.example.demo.controller.dto.FederationUserResponse;
import com.example.demo.controller.dto.FederationValidateRequest;
import com.example.demo.controller.dto.FederationValidateResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/federation/user")
public class FederationUserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String federationApiKey;

    public FederationUserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.federation.api-key:change-me}") String federationApiKey
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.federationApiKey = federationApiKey;
    }

    @PostMapping("/lookup")
    public FederationUserResponse lookup(
            @RequestHeader(value = "X-Federation-Api-Key", required = false) String apiKey,
            @RequestBody FederationLookupRequest request
    ) {
        authorize(apiKey);
        User user = findUserByUsername(request == null ? null : request.username());
        if (user == null) {
            return new FederationUserResponse(false, "", "");
        }
        return new FederationUserResponse(true, safe(user.getEmail()), safe(user.getName()));
    }

    @PostMapping("/validate")
    public FederationValidateResponse validate(
            @RequestHeader(value = "X-Federation-Api-Key", required = false) String apiKey,
            @RequestBody FederationValidateRequest request
    ) {
        authorize(apiKey);
        User user = findUserByUsername(request == null ? null : request.username());
        String password = request == null ? null : request.password();
        if (user == null || password == null || password.isBlank()) {
            return new FederationValidateResponse(false, "", "");
        }

        boolean valid = hasPassword(user) && passwordEncoder.matches(password, user.getPassword());

        if (!valid) {
            return new FederationValidateResponse(false, safe(user.getEmail()), safe(user.getName()));
        }

        return new FederationValidateResponse(true, safe(user.getEmail()), safe(user.getName()));
    }

    private void authorize(String apiKey) {
        if (federationApiKey == null || federationApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Federation API key là không được cấu hình");
        }
        if (apiKey == null || !federationApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid federation API key");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username không được để trống");
        }
        return username.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private User findUserByUsername(String username) {
        return userRepository.findFirstByEmailIgnoreCase(normalizeUsername(username)).orElse(null);
    }

    private boolean hasPassword(User user) {
        return user.getPassword() != null && !user.getPassword().isBlank();
    }
}
