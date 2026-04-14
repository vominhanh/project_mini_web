package com.example.demo.service;

import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.TokenRequest;
import com.example.demo.dto.response.TokenResponse;

import java.util.List;
import java.util.Map;

public interface RemoteFederationAuthService {
    TokenResponse login(TokenRequest request);

    TokenResponse register(RegisterRequest request);

    TokenResponse exchangeGoogleCode(String code, String redirectUri);

    String getGoogleAuthUrl(String redirectUri);

    Map<String, Object> resolveView(String accessToken);

    List<Map<String, Object>> getAllUsers(String accessToken);

    List<Map<String, Object>> getReportUsers(String accessToken);

    Map<String, Object> getInfo(String accessToken);

    String getResetPasswordUrl(String redirectUri);
}
