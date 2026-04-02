package com.example.demo.controller;

import com.example.demo.controller.dto.GoogleCodeExchangeRequest;
import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.service.RemoteFederationAuthService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private final RemoteFederationAuthService remoteFederationAuthService;

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

    @PostMapping("/token/google")
    public TokenResponse tokenGoogle(@RequestBody GoogleCodeExchangeRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Thieu authorization code tu Google");
        }
        if (request.getRedirectUri() == null || request.getRedirectUri().isBlank()) {
            throw new IllegalArgumentException("Thieu redirect URI");
        }
        return remoteFederationAuthService.exchangeGoogleCode(request.getCode(), request.getRedirectUri());
    }

    @GetMapping("/google/auth-url")
    public Map<String, String> googleAuthUrl(@RequestParam String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Thieu redirect URI");
        }
        return Map.of("url", remoteFederationAuthService.getGoogleAuthUrl(redirectUri));
    }
} 

