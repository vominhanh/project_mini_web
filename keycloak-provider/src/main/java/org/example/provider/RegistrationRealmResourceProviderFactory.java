package org.example.provider;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class RegistrationRealmResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String ID = "custom-register";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new RegistrationRealmResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization required.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post initialization required.
    }

    @Override
    public void close() {
        // No resources to close.
    }

    @Override
    public String getId() {
        return ID;
    }
}
