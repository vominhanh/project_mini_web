package com.example.demo.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TokenRoleResolver {
    private TokenRoleResolver() {
    }

    public static List<String> extractRoles(String accessToken) {
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

    public static Map<String, Object> extractCurrentUser(String accessToken) {
        Map<String, Object> claims = parseClaims(accessToken);
        if (claims.isEmpty()) {
            return Map.of();
        }
        List<String> roles = extractRoles(accessToken);
        String effectiveRole = resolveEffectiveRole(roles);
        Map<String, Object> user = new HashMap<>();
        String id = stringValue(claims.get("sub"));
        String username = stringValue(claims.get("preferred_username"));
        if (username.isBlank()) {
            username = stringValue(claims.get("username"));
        }
        String firstName = stringValue(claims.get("given_name"));
        String lastName = stringValue(claims.get("family_name"));
        String name = stringValue(claims.get("name"));
        String email = firstNonBlank(
                stringValue(claims.get("email")),
                stringValue(claims.get("upn")),
                stringValue(claims.get("preferred_username")),
                stringValue(claims.get("username"))
        );

        if (email.isBlank()) {
            email = firstNonBlank(username, name, id);
        }

        if (name.isBlank()) {
            String fullFromNames = (firstName + " " + lastName).trim();
            if (!fullFromNames.isBlank()) {
                name = fullFromNames;
            } else if (!username.isBlank()) {
                name = username;
            } else {
                name = id;
            }
        }

        user.put("id", id);
        user.put("email", email);
        user.put("username", username);
        user.put("name", name);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        user.put("effectiveRole", effectiveRole);
        user.put("roles", roles);
        return user;
    }

    public static Map<String, Object> extractAllUserClaims(String accessToken) {
        return parseClaims(accessToken);
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

    public static String resolveEffectiveRole(List<String> roles) {
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
