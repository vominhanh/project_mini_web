package org.example.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.storage.UserStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class CustomUserStorageProviderFactory implements UserStorageProviderFactory<CustomUserStorageProvider> {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserStorageProviderFactory.class);
    private static final String DEFAULT_URL = "jdbc:postgresql://host.docker.internal:5432/miniWeb";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "123321";

    @Override
    public CustomUserStorageProvider create(KeycloakSession keycloakSession, ComponentModel componentModel) {
        try {
            CustomUserStorageProvider customUserProvider = new CustomUserStorageProvider();
            customUserProvider.setModel(componentModel);
            customUserProvider.setSession(keycloakSession);
            customUserProvider.setConnection(createConnection());
            return customUserProvider;
        } catch (Exception e) {
            logger.error("Error creating CustomerStorageProvider", e);
            throw new RuntimeException("Failed to create CustomerStorageProvider", e);
        }
    }

    private Connection createConnection() {
        int attempts = 0;
        SQLException lastException = null;
        while (attempts < 3) {
            try {
                // Không dùng tiền tố KC_* — Keycloak map KC_* sang cấu hình server và có thể gây lỗi build.
                String jdbcUrl = firstNonBlank(
                        System.getenv("USER_STORAGE_JDBC_URL"),
                        System.getenv("KC_PROVIDER_DB_URL"),
                        DEFAULT_URL);
                String jdbcUser = firstNonBlank(
                        System.getenv("USER_STORAGE_JDBC_USER"),
                        System.getenv("KC_PROVIDER_DB_USER"),
                        DEFAULT_USER);
                String jdbcPassword = firstNonBlank(
                        System.getenv("USER_STORAGE_JDBC_PASSWORD"),
                        System.getenv("KC_PROVIDER_DB_PASSWORD"),
                        DEFAULT_PASSWORD);
                Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                ensureUsersTable(connection);
                logger.info("Successfully connected to the PostgreSQL database.");
                return connection;
            } catch (SQLException e) {
                attempts++;
                lastException = e;
                logger.error("Failed to connect to the database. Attempt: {}", attempts);
            }
        }
        throw new RuntimeException("Unable to connect to the database after 3 attempts", lastException);
    }

    @Override
    public String getId() {
        return "keycloak-provider";
    }

    @Override
    public String getHelpText() {
        return "Keycloak Provider (PostgreSQL users)";
    }

    private void ensureUsersTable(Connection connection) throws SQLException {
        final String ddl = """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    firstname VARCHAR(50) NOT NULL,
                    lastname VARCHAR(50) NOT NULL,
                    email VARCHAR(100) NOT NULL UNIQUE,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    @Override
    public void close() {
        // Connection lifecycle is managed per-provider instance.
    }
}