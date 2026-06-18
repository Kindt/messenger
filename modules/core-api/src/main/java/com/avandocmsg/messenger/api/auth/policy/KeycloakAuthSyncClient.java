package com.avandocmsg.messenger.api.auth.policy;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Sync LDAP user federation and OIDC/SAML identity brokers into Keycloak Admin API. */
public class KeycloakAuthSyncClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthSyncClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig appConfig;
    private final HttpClient httpClient;

    public KeycloakAuthSyncClient(AppConfig appConfig) {
        this(appConfig, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    KeycloakAuthSyncClient(AppConfig appConfig, HttpClient httpClient) {
        this.appConfig = appConfig;
        this.httpClient = httpClient;
    }

    public record ApplyResult(boolean ok, String componentId, String error) {}

    public ApplyResult upsertLdap(String name, Map<String, String> settings) {
        var token = adminToken();
        if (token == null) {
            return new ApplyResult(false, null, "keycloak_admin_token_unavailable");
        }
        try {
            var body = ldapBody(name, settings);
            var base = appConfig.keycloakAdminRealmBase() + "/user-storage";
            var code = post(base, token, body);
            if (code == 409) {
                var existingId = findUserStorageId(token, name);
                if (existingId.isEmpty()) {
                    return new ApplyResult(false, null, "ldap_provider_conflict");
                }
                code = put(base + "/" + existingId.get(), token, body);
                return resultFromCode(code, existingId.get(), "ldap_upsert_failed");
            }
            if (code == 201 || code == 200 || code == 204) {
                var id = findUserStorageId(token, name).orElse(null);
                return new ApplyResult(true, id, null);
            }
            return new ApplyResult(false, null, "ldap_upsert_http_" + code);
        } catch (Exception e) {
            log.warn("upsertLdap failed: {}", e.getMessage());
            return new ApplyResult(false, null, e.getMessage());
        }
    }

    public ApplyResult upsertIdentityProvider(String alias, String providerId, Map<String, String> settings) {
        var token = adminToken();
        if (token == null) {
            return new ApplyResult(false, null, "keycloak_admin_token_unavailable");
        }
        try {
            var body = idpBody(alias, providerId, settings);
            var base = appConfig.keycloakAdminRealmBase() + "/identity-provider/instances";
            var code = post(base, token, body);
            if (code == 409) {
                code = put(base + "/" + urlEncode(alias), token, body);
            }
            if (code == 201 || code == 200 || code == 204) {
                return new ApplyResult(true, alias, null);
            }
            return new ApplyResult(false, null, "idp_upsert_http_" + code);
        } catch (Exception e) {
            log.warn("upsertIdentityProvider failed: {}", e.getMessage());
            return new ApplyResult(false, null, e.getMessage());
        }
    }

    private ApplyResult resultFromCode(int code, String id, String failKey) {
        if (code == 201 || code == 200 || code == 204) {
            return new ApplyResult(true, id, null);
        }
        return new ApplyResult(false, null, failKey + "_http_" + code);
    }

    private ObjectNode ldapBody(String name, Map<String, String> s) {
        var vendor = s.getOrDefault("vendor", "ad");
        var editMode = s.getOrDefault("edit_mode", "READ_ONLY");
        var usernameAttr = "ad".equalsIgnoreCase(vendor) ? "sAMAccountName" : "uid";
        var uuidAttr = "ad".equalsIgnoreCase(vendor) ? "objectGUID" : "entryUUID";
        var userClasses = "ad".equalsIgnoreCase(vendor)
            ? "person, organizationalPerson, user"
            : "inetOrgPerson, organizationalPerson";

        var config = MAPPER.createObjectNode();
        putConfig(config, "enabled", "true");
        putConfig(config, "priority", s.getOrDefault("priority", "0"));
        putConfig(config, "editMode", editMode);
        putConfig(config, "syncRegistrations", "false");
        putConfig(config, "vendor", vendor);
        putConfig(config, "usernameLDAPAttribute", usernameAttr);
        putConfig(config, "rdnLDAPAttribute", "cn");
        putConfig(config, "uuidLDAPAttribute", uuidAttr);
        putConfig(config, "userObjectClasses", userClasses);
        putConfig(config, "connectionUrl", required(s, "connection_url"));
        putConfig(config, "usersDn", required(s, "users_dn"));
        putConfig(config, "bindDn", required(s, "bind_dn"));
        putConfig(config, "bindCredential", required(s, "bind_password"));
        putConfig(config, "searchScope", "2");
        putConfig(config, "useTruststoreSpi", "ldapsOnly");
        putConfig(config, "pagination", "true");

        var root = MAPPER.createObjectNode();
        root.put("name", name);
        root.put("providerId", "ldap");
        root.put("providerType", "org.keycloak.storage.UserStorageProvider");
        root.set("config", config);
        return root;
    }

    private ObjectNode idpBody(String alias, String providerId, Map<String, String> s) {
        var root = MAPPER.createObjectNode();
        root.put("alias", alias);
        root.put("displayName", s.getOrDefault("display_name", alias));
        root.put("providerId", providerId);
        root.put("enabled", true);
        root.put("trustEmail", true);
        root.put("storeToken", false);
        root.put("firstBrokerLoginFlowAlias", "first broker login");

        var config = MAPPER.createObjectNode();
        if ("oidc".equals(providerId)) {
            putConfig(config, "clientId", required(s, "client_id"));
            putConfig(config, "clientSecret", required(s, "client_secret"));
            putConfig(config, "discoveryUrl", required(s, "discovery_url"));
            putConfig(config, "clientAuthMethod", "client_secret_post");
            putConfig(config, "defaultScope", "openid profile email");
            putConfig(config, "syncMode", "IMPORT");
            putConfig(config, "useJwksUrl", "true");
        } else if ("saml".equals(providerId)) {
            putConfig(config, "singleSignOnServiceUrl", required(s, "sso_url"));
            putConfig(config, "entityId", s.getOrDefault("entity_id", alias));
            if (s.containsKey("signing_certificate")) {
                putConfig(config, "signingCertificate", s.get("signing_certificate"));
            }
            putConfig(config, "wantAssertionsSigned", "true");
            putConfig(config, "syncMode", "IMPORT");
        } else {
            throw new IllegalArgumentException("unsupported_idp_type:" + providerId);
        }
        root.set("config", config);
        return root;
    }

    private static void putConfig(ObjectNode config, String key, String value) {
        var arr = MAPPER.createArrayNode();
        arr.add(value);
        config.set(key, arr);
    }

    private static String required(Map<String, String> s, String key) {
        var v = s.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("missing_setting:" + key);
        }
        return v;
    }

    private int post(String url, String token, ObjectNode body) throws Exception {
        return send("POST", url, token, body);
    }

    private int put(String url, String token, ObjectNode body) throws Exception {
        return send("PUT", url, token, body);
    }

    private int send(String method, String url, String token, ObjectNode body) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(15));
        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body.toString()));
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.warn("Keycloak {} {} -> {} body={}", method, url, response.statusCode(), truncate(response.body()));
        }
        return response.statusCode();
    }

    private Optional<String> findUserStorageId(String token, String name) throws Exception {
        var url = appConfig.keycloakAdminRealmBase() + "/user-storage";
        var req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        var response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return Optional.empty();
        }
        JsonNode arr = MAPPER.readTree(response.body());
        if (!arr.isArray()) {
            return Optional.empty();
        }
        for (JsonNode node : arr) {
            if (name.equals(node.path("name").asText())) {
                return Optional.ofNullable(node.path("id").asText(null));
            }
        }
        return Optional.empty();
    }

    private String adminToken() {
        try {
            var body = "client_id=admin-cli"
                + "&username=" + urlEncode(appConfig.keycloakMasterUser())
                + "&password=" + urlEncode(appConfig.keycloakMasterPassword())
                + "&grant_type=password";
            var request = HttpRequest.newBuilder()
                .uri(URI.create(appConfig.keycloakMasterTokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return MAPPER.readTree(response.body()).path("access_token").asText(null);
            }
        } catch (Exception e) {
            log.warn("admin token failed: {}", e.getMessage());
        }
        return null;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
