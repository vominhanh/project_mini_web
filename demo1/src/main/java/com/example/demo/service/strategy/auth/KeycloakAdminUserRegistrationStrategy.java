package com.example.demo.service.strategy.auth;

import com.example.demo.constant.auth.UserRegistrationConstants;
import com.example.demo.client.keycloak.KeycloakAdminApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đăng ký qua Keycloak Admin REST khi đã cấu hình client admin.
 */
@Component
@RequiredArgsConstructor
public class KeycloakAdminUserRegistrationStrategy {

    private final KeycloakAdminApiClient keycloakAdminApiClient;

    public void createUser(String email, String password, String firstName, String lastName) {
        String fn = normalizeFirstName(firstName);
        String ln = normalizeLastName(lastName);
        String username = buildUsername(fn, ln);
        String adminToken = keycloakAdminApiClient.obtainAdminAccessToken();
        keycloakAdminApiClient.createPasswordUser(adminToken, email, username, password, fn, ln);
    }

    private static String normalizeFirstName(String firstName) {
        String fn = (firstName != null && !firstName.isBlank()) ? firstName.trim() : UserRegistrationConstants.DEFAULT_FIRST_NAME;
        return truncate(fn, UserRegistrationConstants.MAX_NAME_FIELD_LENGTH);
    }

    private static String normalizeLastName(String lastName) {
        String ln = (lastName != null && !lastName.isBlank()) ? lastName.trim() : UserRegistrationConstants.DEFAULT_LAST_NAME;
        return truncate(ln, UserRegistrationConstants.MAX_NAME_FIELD_LENGTH);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String buildUsername(String firstName, String lastName) {
        String raw = (firstName + lastName).trim();
        if (raw.isBlank()) {
            return "user";
        }
        String ascii = raw
                .replaceAll("\\s+", "")
                .replaceAll("[^A-Za-z0-9._-]", "")
                .toLowerCase();
        if (ascii.isBlank()) {
            return "user";
        }
        return truncate(ascii, 150);
    }
}
