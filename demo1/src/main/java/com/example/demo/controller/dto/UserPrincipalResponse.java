package com.example.demo.controller.dto;

import java.util.Map;

public record UserPrincipalResponse(
        String sub,
        String email,
        String preferredUsername,
        Map<String, Object> claims
) {
}

