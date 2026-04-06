package com.example.demo.service;

import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;
import com.example.demo.controller.dto.RegisterRequest;

import java.util.List;
import java.util.Map;

public interface RemoteFederationAuthService {
    TokenResponse login(TokenRequest request);

    TokenResponse register(RegisterRequest request);

    TokenResponse exchangeGoogleCode(String code, String redirectUri);

    String getGoogleAuthUrl(String redirectUri);

    Map<String, Object> resolveView(String accessToken);

    List<Map<String, Object>> getAllUsers(String accessToken);

    Map<String, Object> getInfo(String accessToken);

    String getResetPasswordUrl(String redirectUri);
}
