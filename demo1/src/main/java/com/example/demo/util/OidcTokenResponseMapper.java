package com.example.demo.util;

import com.example.demo.dto.response.TokenResponse;

import java.util.List;
import java.util.Map;

/**
 * Map body JSON token endpoint OIDC → {@link TokenResponse} (DRY).
 */
public final class OidcTokenResponseMapper {

    private OidcTokenResponseMapper() {
    }

    public static TokenResponse fromTokenEndpointBody(Map<String, Object> body) {
        Object accessToken = body == null ? null : body.get("access_token");
        Object refreshToken = body == null ? null : body.get("refresh_token");
        Object expiresIn = body == null ? null : body.get("expires_in");
        Object tokenType = body == null ? null : body.get("token_type");
        String token = accessToken == null ? "" : accessToken.toString();
        List<String> roles = TokenRoleResolver.extractRoles(token);

        return new TokenResponse(
                token,
                refreshToken == null ? null : refreshToken.toString(),
                expiresIn == null ? 0 : Integer.parseInt(expiresIn.toString()),
                tokenType == null ? "Bearer" : tokenType.toString(),
                TokenRoleResolver.resolveEffectiveRole(roles),
                roles
        );
    }
}
