package com.example.demo.service.impl;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TokenRoleResolver {
    private TokenRoleResolver() {
    }

    static List<String> extractRoles(String accessToken) {
        Map<String, Object> claims = parseClaims(accessToken);
        if (claims.isEmpty()) {
            return List.of();
        }

        Set<String> roles = new LinkedHashSet<>();
        Object realmAccessObj = claims.get("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List<?> roleList) {
                for (Object role : roleList) {
                    if (role != null) {
                        roles.add(role.toString());
                    }
                }
            }
        }

        Object resourceAccessObj = claims.get("resource_access");
        if (resourceAccessObj instanceof Map<?, ?> resourceAccess) {
            for (Object value : resourceAccess.values()) {
                if (value instanceof Map<?, ?> clientAccess) {
                    Object rolesObj = clientAccess.get("roles");
                    if (rolesObj instanceof List<?> roleList) {
                        for (Object role : roleList) {
                            if (role != null) {
                                roles.add(role.toString());
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(roles);
    }

    static Map<String, Object> extractCurrentUser(String accessToken) {
        Map<String, Object> claims = parseClaims(accessToken);
        if (claims.isEmpty()) {
            return Map.of();
        }
        List<String> roles = extractRoles(accessToken);
        String effectiveRole = resolveEffectiveRole(roles);
        Map<String, Object> user = new HashMap<>();
        user.put("id", stringValue(claims.get("sub")));
        user.put("email", stringValue(claims.get("email")));
        user.put("username", stringValue(claims.get("preferred_username")));
        user.put("name", stringValue(claims.get("name")));
        user.put("firstName", stringValue(claims.get("given_name")));
        user.put("lastName", stringValue(claims.get("family_name")));
        user.put("effectiveRole", effectiveRole);
        user.put("roles", roles);
        return user;
    }

    private static Map<String, Object> parseClaims(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();
            Map<String, Object> claims = claimsSet.getClaims();
            if (claims == null || claims.isEmpty()) {
                return Map.of();
            }
            return claims;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    static String resolveEffectiveRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "user";
        }
        for (String role : roles) {
            if (role != null && role.toLowerCase(Locale.ROOT).contains("admin")) {
                return "admin";
            }
        }
        return "user";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
