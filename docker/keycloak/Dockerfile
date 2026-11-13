FROM quay.io/keycloak/keycloak:26.7.4

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true

COPY --chown=keycloak:keycloak themes/edol /opt/keycloak/themes/edol

RUN /opt/keycloak/bin/kc.sh build
