package com.example.demo.constant.auth;

/**
 * Giới hạn / giá trị mặc định khi đăng ký user (khớp schema Keycloak user-storage).
 */
public final class UserRegistrationConstants {

    public static final int MAX_EMAIL_LENGTH = 50;
    public static final int MAX_NAME_FIELD_LENGTH = 50;
    public static final int BCRYPT_COST = 12;
    public static final String DEFAULT_FIRST_NAME = "User";
    public static final String DEFAULT_LAST_NAME = "Keycloak";

    private UserRegistrationConstants() {
    }
}
