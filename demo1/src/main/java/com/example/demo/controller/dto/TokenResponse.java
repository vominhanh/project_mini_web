package com.example.demo.controller.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String tokenType
) {
}

