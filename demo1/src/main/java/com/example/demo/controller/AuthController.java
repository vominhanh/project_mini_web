package com.example.demo.controller;

import com.example.demo.controller.dto.GoogleCodeExchangeRequest;
import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.controller.dto.RegisterRequest;
import com.example.demo.service.RemoteFederationAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private final RemoteFederationAuthService remoteFederationAuthService;
    @Value("${app.auth.google.redirect-uri:http://localhost:4200/}")
    private String defaultGoogleRedirectUri;

    public AuthController(RemoteFederationAuthService remoteFederationAuthService) {
        this.remoteFederationAuthService = remoteFederationAuthService;
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest request) {
        return tokenRemoteFederation(request);
    }

    @PostMapping("/token/remote-federation")
    public TokenResponse tokenRemoteFederation(@RequestBody TokenRequest request) {
        return remoteFederationAuthService.login(request);
    }

    @PostMapping("/register/remote-federation")
    public TokenResponse registerRemoteFederation(@RequestBody RegisterRequest request) {
        return remoteFederationAuthService.register(request);
    }

    @PostMapping("/token/google")
    public TokenResponse tokenGoogle(@RequestBody GoogleCodeExchangeRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Thieu authorization code tu Google");
        }
        String redirectUri = resolveRedirectUri(request.getRedirectUri());
        return remoteFederationAuthService.exchangeGoogleCode(request.getCode(), redirectUri);
    }

    @GetMapping("/google/auth-url")
    public Map<String, String> googleAuthUrl(@RequestParam(required = false) String redirectUri) {
        redirectUri = resolveRedirectUri(redirectUri);
        return Map.of("url", remoteFederationAuthService.getGoogleAuthUrl(redirectUri));
    }

    @GetMapping("/forgot-password/url")
    public Map<String, String> forgotPasswordUrl(@RequestParam(required = false) String redirectUri) {
        redirectUri = resolveRedirectUri(redirectUri);
        return Map.of("url", remoteFederationAuthService.getResetPasswordUrl(redirectUri));
    }

    @GetMapping("/view")
    public Map<String, Object> view(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        return remoteFederationAuthService.resolveView(token);
    }

    @GetMapping("/me")
    public java.util.List<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        return remoteFederationAuthService. getAllUsers(token);
    }

    @GetMapping("/getinfo")
    public Map<String, Object> getInfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        return remoteFederationAuthService.getInfo(token);
    }

    private String resolveRedirectUri(String redirectUri) {
        if (redirectUri != null && !redirectUri.isBlank()) {
            return redirectUri.trim();
        }
        if (defaultGoogleRedirectUri != null && !defaultGoogleRedirectUri.isBlank()) {
            return defaultGoogleRedirectUri.trim();
        }
        throw new IllegalArgumentException("Thieu redirect URI");
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return "";
        }
        String bearerPrefix = "Bearer ";
        if (authorization.startsWith(bearerPrefix)) {
            return authorization.substring(bearerPrefix.length()).trim();
        }
        return authorization.trim();
    }
} 

