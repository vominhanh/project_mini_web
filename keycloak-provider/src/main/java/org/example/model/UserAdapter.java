package org.example.model;


import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private final String id;
    private final User user;
    private final Connection connection;

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel storageProviderModel, User user, Connection connection) {
        super(session, realm, storageProviderModel);
        this.user = user;
        this.id = StorageId.keycloakId(storageProviderModel, user.getId().toString());
        this.connection = connection;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public void setUsername(String s) {
        String normalized = s == null ? null : s.trim().toLowerCase(Locale.ROOT);
        user.setUsername(normalized);
        updateDatabase("username", normalized);
    }

    @Override
    public String getId(){
        return id;
    }

    @Override
    public String getEmail(){
        return user.getEmail();
    }

    @Override
    public String getFirstName() {
        return user.getFirstName();
    }

    @Override
    public String getLastName() {
        return user.getLastName();
    }

    public String getPassword() {
        return user.getPassword();
    }

    @Overridedocker build -t keycloak-custom:latest .
    docker run --rm -p 8080:8080 keycloak-custom:latest
    public void setEmail(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        user.setEmail(normalized);
        super.setEmail(normalized);
        updateDatabase("email", normalized);
    }

    @Override
    public void setFirstName(String firstName) {
        super.setFirstName(firstName);
        updateDatabase("firstname", firstName);
    }

    @Override
    public void setLastName(String lastName) {
        super.setLastName(lastName);
        updateDatabase("lastname", lastName);
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        super.setAttribute(name, values);
        if ("firstName".equals(name)) {
            updateDatabase("firstname", values.get(0));
        } else if ("lastName".equals(name)) {
            updateDatabase("lastname", values.get(0));
        } else if ("email".equals(name)) {
            updateDatabase("email", values.get(0));
        }
    }

    private void updateDatabase(String field, String value) {
        String query = "UPDATE users SET " + field + " = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, value);
            stmt.setLong(2, Long.parseLong(StorageId.externalId(getId())));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

//    private void updateDatabase(String field, String value) {
//        List<String> validFields = Arrays.asList("name", "email", "phone");
//        if (!validFields.contains(field)) {
//            throw new IllegalArgumentException("Invalid field: " + field);
//        }
//
//        String query = "UPDATE users SET " + field + " = ? WHERE id = ?";
//        try (PreparedStatement stmt = connection.prepareStatement(query)) {
//            stmt.setString(1, value);
//            stmt.setLong(2, Long.parseLong(StorageId.externalId(getId())));
//            int rowsUpdated = stmt.executeUpdate();
//
//            if (rowsUpdated > 0) {
//                System.out.println("Update successful: " + rowsUpdated + " rows updated.");
//            } else {
//                System.out.println("No rows were updated.");
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException("Database update failed", e);
//        }
//    }
}