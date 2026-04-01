FROM maven:3.9.9-eclipse-temurin-17 AS provider-build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

FROM quay.io/keycloak/keycloak:26.0.7

USER root
RUN mkdir -p /opt/keycloak/providers/
COPY --from=provider-build /workspace/target/ASM_Provider-1.0-SNAPSHOT.jar /opt/keycloak/providers/
COPY --from=provider-build /workspace/target/dependency/*.jar /opt/keycloak/providers/
RUN chown -R keycloak:keycloak /opt/keycloak/providers/

USER keycloak
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KEYCLOAK_ADMIN=admin
ENV KEYCLOAK_ADMIN_PASSWORD=admin123
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev"]