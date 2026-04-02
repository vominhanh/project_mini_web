package org.example.provider;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.example.model.User;
import org.example.model.UserAdapter;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class CustomUserStorageProvider implements UserStorageProvider, UserRegistrationProvider, UserQueryProvider,
        UserLookupProvider, CredentialInputValidator, CredentialInputUpdater {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomUserStorageProvider.class);

    private ComponentModel componentModel;
    private KeycloakSession keycloakSession;
    private Connection connection;

    public void setModel(ComponentModel componentModel) {
        this.componentModel = componentModel;
    }

    public void setSession(KeycloakSession keycloakSession) {
        this.keycloakSession = keycloakSession;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    private boolean hasConnection() {
        if (connection == null) {
            logger.error("Database connection is not initialized.");
            return false;
        }
        return true;
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        // email thường map 1-1 với username theo cách bạn dùng ở FE/API
        return normalizeUsername(email);
    }

    @Override
    public void close() {
        logger.info("Closing database connection.");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed successfully.");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }

    @Override
    public UserModel getUserByUsername(RealmModel realmModel, String username) {
        if (!hasConnection()) {
            return null;
        }
        String normalizedUsername = normalizeUsername(username);
        logger.info("Attempting to find user by username: {}", normalizedUsername);
        String query = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, normalizedUsername);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                logger.info("User found with username: {}", normalizedUsername);
                User user = mapRowToUser(rs);
                return new UserAdapter(keycloakSession, realmModel, componentModel, user, connection);
            } else {
                logger.warn("No user found with username: {}", normalizedUsername);
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username", e);
        }
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        if (!hasConnection()) {
            return null;
        }
        String normalizedEmail = normalizeEmail(email);
        logger.info("Attempting to find user by email: {}", normalizedEmail);
        String query = "SELECT * FROM users WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, normalizedEmail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                logger.info("User found with email: {}", normalizedEmail);
                User user = mapRowToUser(rs);
                return new UserAdapter(keycloakSession, realmModel, componentModel, user, connection);
            } else {
                logger.warn("No user found with email: {}", normalizedEmail);
            }
        } catch (SQLException e) {
            logger.error("Error finding user by email", e);
        }
        return null;
    }

    @Override
    public UserModel getUserById(RealmModel realmModel, String id) {
        if (!hasConnection()) {
            return null;
        }
        logger.info("Attempting to find user by ID: {}", id);
        long persistenceId;
        try {
            persistenceId = Long.parseLong(StorageId.externalId(id));
        } catch (NumberFormatException e) {
            logger.error("Invalid ID format: {}", id, e);
            return null;
        }

        String query = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, persistenceId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                logger.info("User found with ID: {}", id);
                User user = mapRowToUser(rs);
                return new UserAdapter(keycloakSession, realmModel, componentModel, user, connection);
            } else {
                logger.warn("No user found with ID: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error finding user by ID", e);
        }
        return null;
    }

    @Override
    public UserModel addUser(RealmModel realmModel, String username) {
        if (!hasConnection()) {
            return null;
        }
        logger.info("Attempting to add user with username: {}", username);
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty()) {
            logger.warn("Cannot add user with empty username.");
            return null;
        }

        String firstName = "User";
        String lastName = "Keycloak";
        String email = normalizedUsername + "@pending.local";
        String randomPassword = hashPassword(UUID.randomUUID().toString());

        String query = "INSERT INTO users (username, email, firstname, lastname, password) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, normalizedUsername);
            stmt.setString(2, email);
            stmt.setString(3, firstName);
            stmt.setString(4, lastName);
            stmt.setString(5, randomPassword);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    logger.info("User successfully added with ID: {} and username: {}", id, normalizedUsername);

                    User user = new User();
                    user.setId(id);
                    user.setUsername(normalizedUsername);
                    user.setEmail(email);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setPassword(randomPassword);

                    return new UserAdapter(keycloakSession, realmModel, componentModel, user, connection);
                } else {
                    logger.error("Failed to retrieve generated ID for user: {}", normalizedUsername);
                }
            }
        } catch (SQLException e) {
            logger.error("Error adding user", e);
        }
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realmModel, UserModel userModel) {
        if (!hasConnection()) {
            return false;
        }
        logger.info("Attempting to remove user with ID: {}", userModel.getId());
        String query = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            long persistenceId;
            try {
                persistenceId = Long.parseLong(StorageId.externalId(userModel.getId()));
            } catch (NumberFormatException e) {
                logger.error("Invalid ID format: {}", userModel.getId(), e);
                return false;
            }

            stmt.setLong(1, persistenceId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("User successfully removed with ID: {}", userModel.getId());
                return true;
            } else {
                logger.warn("No user found with ID: {} to remove", userModel.getId());
            }
        } catch (SQLException e) {
            logger.error("Error removing user", e);
        }
        return false;
    }

    // @Override
    // public boolean supportsCredentialType(String credentialType) {
    // return "password".equals(credentialType);
    // }
    //
    // @Override
    // public boolean isConfiguredFor(RealmModel realm, UserModel user, String
    // credentialType) {
    // return supportsCredentialType(credentialType);
    // }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        if (!hasConnection()) {
            return false;
        }
        if (credentialInput == null) {
            logger.warn("Credential input is null for user: {}", user != null ? user.getUsername() : "<null>");
            return false;
        }
        logger.info("Validating credentials for user: {}", user.getUsername());
        if (!supportsCredentialType(credentialInput.getType())) {
            logger.warn("Unsupported credential type: {}", credentialInput.getType());
            return false;
        }
        String query = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, normalizeUsername(user.getUsername()));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                boolean isValid = verifyPassword(credentialInput.getChallengeResponse(), storedPassword);
                logger.info("Credential validation result for user {}: {}", user.getUsername(), isValid);
                return isValid;
            } else {
                logger.warn("No password found for user: {}", user.getUsername());
            }
        } catch (SQLException e) {
            logger.error("Error validating credentials for user", e);
        }
        return false;
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realmModel, Map<String, String> map, Integer firstResult,
            Integer maxResults) {
        if (!hasConnection()) {
            return Stream.empty();
        }
        String searchParam = map.getOrDefault("email", map.getOrDefault("username", ""));
        searchParam = searchParam == null ? "" : searchParam.trim().toLowerCase(Locale.ROOT);
        logger.info("Searching for users with parameter: {}", searchParam);

        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM users WHERE email LIKE ? OR username LIKE ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, "%" + searchParam + "%");
            stmt.setString(2, "%" + searchParam + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
            logger.info("Found {} users matching search parameter: {}", users.size(), searchParam);
        } catch (SQLException e) {
            logger.error("Error searching for users", e);
        }

        return users.stream()
                .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user, connection));
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        logger.info("Mapping ResultSet to User object");
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setFirstName(rs.getString("firstname"));
        user.setLastName(rs.getString("lastname"));
        user.setPassword(rs.getString("password"));
        logger.info("Mapped User: {}", user.getUsername());
        return user;
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realmModel, GroupModel groupModel, Integer firstResult,
            Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realmModel, String attribute, String value) {
        return Stream.empty();
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
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (!hasConnection()) {
            return false;
        }
        if (input instanceof UserCredentialModel && CredentialModel.PASSWORD.equals(input.getType())) {
            String newPassword = ((UserCredentialModel) input).getValue();
            String hashedPassword = hashPassword(newPassword);

            String query = "UPDATE users SET password = ? WHERE username = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, hashedPassword);
                stmt.setString(2, normalizeUsername(user.getUsername()));
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated > 0) {
                    logger.info("Password updated successfully for user: {}", user.getUsername());
                    return true;
                } else {
                    logger.warn("Failed to update password for user: {}", user.getUsername());
                    return false;
                }
            } catch (SQLException e) {
                logger.error("Error updating password for user", e);
                return false;
            }
        } else {
            logger.warn("Unsupported credential type for update: {}", input.getType());
            throw new IllegalArgumentException("Unsupported credential type: " + input.getType());
        }
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        if (CredentialModel.PASSWORD.equals(credentialType)) {
            logger.warn("Disabling credential type 'password' is not supported.");
            throw new UnsupportedOperationException("Disabling password credentials is not supported.");
        }
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        if (!hasConnection()) {
            return 0;
        }

        String query = "SELECT COUNT(*) AS total FROM users";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            logger.error("Error counting users", e);
        }
        return 0;
    }

    private String hashPassword(String plainPassword) {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
    }

    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }

}
