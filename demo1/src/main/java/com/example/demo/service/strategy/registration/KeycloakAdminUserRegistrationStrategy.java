package com.example.demo.service.strategy.registration;

import com.example.demo.constant.UserRegistrationConstants;
import com.example.demo.service.keycloak.KeycloakAdminApiClient;
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
        String adminToken = keycloakAdminApiClient.obtainAdminAccessToken();
        keycloakAdminApiClient.createPasswordUser(adminToken, email, password, fn, ln);
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
}
