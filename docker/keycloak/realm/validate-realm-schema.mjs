import { readFileSync } from "node:fs";

const realmPath = new URL("./edol-realm.json", import.meta.url);
const realm = JSON.parse(readFileSync(realmPath, "utf8"));

const allowedRealmKeys = new Set([
    "realm",
    "enabled",
    "displayName",
    "sslRequired",
    "registrationAllowed",
    "registrationEmailAsUsername",
    "rememberMe",
    "verifyEmail",
    "resetPasswordAllowed",
    "loginWithEmailAllowed",
    "duplicateEmailsAllowed",
    "bruteForceProtected",
    "ssoSessionIdleTimeout",
    "ssoSessionMaxLifespan",
    "accessTokenLifespan",
    "defaultDefaultClientScopes",
    "defaultOptionalClientScopes",
    "smtpServer",
    "clients"
]);

const allowedClientKeys = new Set([
    "clientId",
    "name",
    "enabled",
    "protocol",
    "publicClient",
    "clientAuthenticatorType",
    "secret",
    "standardFlowEnabled",
    "implicitFlowEnabled",
    "directAccessGrantsEnabled",
    "serviceAccountsEnabled",
    "authorizationServicesEnabled",
    "redirectUris",
    "webOrigins",
    "defaultClientScopes",
    "optionalClientScopes",
    "attributes"
]);

function requireCondition(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

function requireKnownKeys(value, allowedKeys, description) {
    for (const key of Object.keys(value)) {
        requireCondition(
            allowedKeys.has(key),
            `${description} contains a Keycloak 26.7.4-unsupported or unreviewed field: ${key}`
        );
    }
}

requireKnownKeys(realm, allowedRealmKeys, "Realm representation");
requireCondition(realm.realm === "edol", "Expected the edol realm");
requireCondition(Array.isArray(realm.clients) && realm.clients.length === 1, "Expected one client");

const [client] = realm.clients;
requireKnownKeys(client, allowedClientKeys, "Client representation");
requireCondition(client.clientId === "edol-hub-web", "Expected the edol-hub-web client");
requireCondition(client.standardFlowEnabled === true, "Authorization Code flow must remain enabled");
requireCondition(client.implicitFlowEnabled === false, "Implicit flow must remain disabled");
requireCondition(client.directAccessGrantsEnabled === false, "Direct access grants must remain disabled");
requireCondition(client.serviceAccountsEnabled === false, "Service accounts must remain disabled");
requireCondition(client.authorizationServicesEnabled === false, "Authorization services must remain disabled");
requireCondition(
    !Object.hasOwn(client, "standardTokenExchangeEnabled"),
    "standardTokenExchangeEnabled is not a Keycloak 26.7.4 ClientRepresentation field"
);
requireCondition(
    !Object.hasOwn(client, "oauth2DeviceAuthorizationGrantEnabled"),
    "oauth2DeviceAuthorizationGrantEnabled is not a Keycloak 26.7.4 ClientRepresentation field"
);
requireCondition(
    client.attributes?.["pkce.code.challenge.method"] === "S256",
    "PKCE S256 must remain required"
);
requireCondition(
    client.attributes?.["oauth2.device.authorization.grant.enabled"] === "false",
    "Device Authorization Grant must remain disabled"
);
requireCondition(
    !client.defaultClientScopes.includes("offline_access") && !client.optionalClientScopes.includes("offline_access"),
    "offline_access must not be available to the client"
);

console.log("Keycloak realm schema and security profile validated.");
