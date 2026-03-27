package com.myproject.keycloak.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import org.keycloak.storage.user.UserLookupProvider;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbUserStorageProvider implements UserStorageProvider, UserLookupProvider, CredentialInputValidator {
    private final KeycloakSession session;
    private final ComponentModel model;

    public DbUserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        return externalId == null ? null : getUserByUsername(realm, externalId);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        DbRow row = findByEmail(username);
        if (row == null) return null;
        return adapt(realm, row);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        DbRow row = findByEmail(email);
        if (row == null) return null;
        return adapt(realm, row);
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return CredentialModel.PASSWORD.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (user == null || input == null || input.getChallengeResponse() == null) return false;
        if (!supportsCredentialType(input.getType())) return false;
        DbRow row = findByEmail(user.getUsername());
        if (row == null || row.password == null || row.password.isBlank()) return false;
        try {
            return BCrypt.checkpw(input.getChallengeResponse(), row.password);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void close() {}

    private UserModel adapt(RealmModel realm, DbRow row) {
        return new AbstractUserAdapterFederatedStorage(session, realm, model) {
            @Override public String getUsername() { return row.email; }
            @Override public void setUsername(String username) {}
            @Override public String getEmail() { return row.email; }
            @Override public String getFirstName() { return row.name; }
        };
    }

    private DbRow findByEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String dbUrl = model.get(DbUserStorageProviderFactory.CFG_DB_URL);
        String dbUser = model.get(DbUserStorageProviderFactory.CFG_DB_USERNAME);
        String dbPass = model.get(DbUserStorageProviderFactory.CFG_DB_PASSWORD);
        String sql = "select email, name, password from app_user where lower(email)=lower(?)";
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DbRow(rs.getString("email"), rs.getString("name"), rs.getString("password"));
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private record DbRow(String email, String name, String password) {}
}
