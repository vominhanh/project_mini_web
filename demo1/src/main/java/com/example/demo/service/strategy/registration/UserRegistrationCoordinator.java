package com.example.demo.service.strategy.registration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Đăng ký user qua Keycloak Admin API.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationCoordinator {

    private static final String NOT_CONFIGURED = "Chua cau hinh dang ky: dat KEYCLOAK_ADMIN_CLIENT_ID + KEYCLOAK_ADMIN_CLIENT_SECRET.";

    private final KeycloakAdminUserRegistrationStrategy keycloakAdminUserRegistrationStrategy;

    public void ensureUserRegistered(String email, String password, String firstName, String lastName) {
        if (keycloakAdminUserRegistrationStrategy != null) {
            keycloakAdminUserRegistrationStrategy.createUser(email, password, firstName, lastName);
            return;
        }

        throw new IllegalStateException(NOT_CONFIGURED);
    }
}
