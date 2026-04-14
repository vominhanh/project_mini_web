package com.example.demo.constant.auth;

/**
 * Scope và tham số luồng OIDC / Keycloak browser client.
 */
public final class KeycloakOAuthConstants {

    public static final String SCOPE_BROWSER = "openid profile email roles offline_access";
    public static final String SCOPE_PASSWORD_GRANT = "openid profile email roles";
    public static final String PROMPT_SELECT_ACCOUNT = "select_account";
    public static final String KC_IDP_HINT_GOOGLE = "google";
    public static final String RESPONSE_TYPE_CODE = "code";
    public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String CREDENTIAL_TYPE_PASSWORD = "password";

    private KeycloakOAuthConstants() {
    }
}
