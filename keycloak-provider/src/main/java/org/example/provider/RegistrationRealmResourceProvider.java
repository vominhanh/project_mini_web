package org.example.provider;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Path("/")
public class RegistrationRealmResourceProvider implements RealmResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationRealmResourceProvider.class);
    private static final String DEFAULT_URL = "jdbc:postgresql://host.docker.internal:5432/miniWeb";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "123321";

    private final KeycloakSession session;

    public RegistrationRealmResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
        // Connection is per-request via try-with-resources.
    }

    @OPTIONS
    @Path("register")
    public Response preflight(@Context HttpHeaders headers) {
        return cors(Response.ok(), headers).build();
    }

    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(RegisterRequest request, @Context HttpHeaders headers) {
        if (request == null) {
            return cors(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid_request", "message", "Request body is required")), headers)
                    .build();
        }

        String email = normalizeEmail(request.getEmail());
        String firstName = normalizeName(request.getFirstName(), "User");
        String lastName = normalizeName(request.getLastName(), "Keycloak");
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String confirmPassword = request.getConfirmPassword() == null ? "" : request.getConfirmPassword().trim();

        if (email == null || email.isBlank()) {
            return cors(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "validation_error", "message", "Email is required")), headers)
                    .build();
        }

        if (password.length() < 6) {
            return cors(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "validation_error", "message", "Password must be at least 6 characters")), headers)
                    .build();
        }

        if (!password.equals(confirmPassword)) {
            return cors(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "validation_error", "message", "Password confirmation does not match")), headers)
                    .build();
        }

        try (Connection connection = createConnection()) {
            ensureUsersTable(connection);

            if (existsByEmail(connection, email)) {
                return cors(Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "duplicate_user", "message", "Email already exists")), headers)
                        .build();
            }

            String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());
            String username = email;

            String insertSql = "INSERT INTO users (firstname, lastname, email, username, password) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, email);
                stmt.setString(4, username);
                stmt.setString(5, hashedPassword);
                stmt.executeUpdate();
            }

            logger.info("Registered user {} in external users table", email);
            return cors(Response.status(Response.Status.CREATED)
                    .entity(Map.of("message", "Registration successful", "username", username)), headers)
                    .build();
        } catch (SQLException ex) {
            logger.error("Registration failed for user {}", email, ex);
            return cors(Response.serverError()
                    .entity(Map.of("error", "server_error", "message", "Registration failed")), headers)
                    .build();
        }
    }

    private boolean existsByEmail(Connection connection, String email) throws SQLException {
        String checkSql = "SELECT 1 FROM users WHERE email = ? OR username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(checkSql)) {
            stmt.setString(1, email);
            stmt.setString(2, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
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
        connection.createStatement().execute(ddl);
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(
                getSetting("KC_PROVIDER_DB_URL", DEFAULT_URL),
                getSetting("KC_PROVIDER_DB_USER", DEFAULT_USER),
                getSetting("KC_PROVIDER_DB_PASSWORD", DEFAULT_PASSWORD)
        );
    }

    private String getSetting(String envKey, String fallback) {
        return Optional.ofNullable(System.getenv(envKey))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(fallback);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private Response.ResponseBuilder cors(Response.ResponseBuilder builder, HttpHeaders headers) {
        String origin = headers.getHeaderString("Origin");
        String allowOrigin = (origin == null || origin.isBlank()) ? "*" : origin;
        return builder
                .header("Access-Control-Allow-Origin", allowOrigin)
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .header("Access-Control-Max-Age", "3600");
    }
}
