package com.myproject.keycloak.provider;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.ArrayList;
import java.util.List;

public class DbUserStorageProviderFactory implements UserStorageProviderFactory<DbUserStorageProvider> {
    public static final String PROVIDER_ID = "db-user-storage-provider";
    public static final String CFG_DB_URL = "dbUrl";
    public static final String CFG_DB_USERNAME = "dbUsername";
    public static final String CFG_DB_PASSWORD = "dbPassword";

    private static final List<ProviderConfigProperty> CONFIG = new ArrayList<>();
    static {
        ProviderConfigProperty dbUrl = new ProviderConfigProperty();
        dbUrl.setName(CFG_DB_URL);
        dbUrl.setLabel("DB URL");
        dbUrl.setType(ProviderConfigProperty.STRING_TYPE);
        dbUrl.setDefaultValue("jdbc:postgresql://localhost:5432/miniWeb");
        CONFIG.add(dbUrl);

        ProviderConfigProperty dbUser = new ProviderConfigProperty();
        dbUser.setName(CFG_DB_USERNAME);
        dbUser.setLabel("DB Username");
        dbUser.setType(ProviderConfigProperty.STRING_TYPE);
        dbUser.setDefaultValue("postgres");
        CONFIG.add(dbUser);

        ProviderConfigProperty dbPass = new ProviderConfigProperty();
        dbPass.setName(CFG_DB_PASSWORD);
        dbPass.setLabel("DB Password");
        dbPass.setType(ProviderConfigProperty.PASSWORD);
        CONFIG.add(dbPass);
    }

    @Override
    public DbUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new DbUserStorageProvider(session, model);
    }

    @Override public String getId() { return PROVIDER_ID; }
    @Override public List<ProviderConfigProperty> getConfigProperties() { return CONFIG; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
