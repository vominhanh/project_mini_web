package com.example.demo.service;

import com.example.demo.controller.dto.TokenRequest;
import com.example.demo.controller.dto.TokenResponse;

public interface RemoteFederationAuthService {
    TokenResponse login(TokenRequest request);

    TokenResponse exchangeGoogleCode(String code, String redirectUri);

    String getGoogleAuthUrl(String redirectUri);
}
