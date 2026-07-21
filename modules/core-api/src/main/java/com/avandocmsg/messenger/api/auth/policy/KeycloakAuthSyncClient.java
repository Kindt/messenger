package com.avandocmsg.messenger.api.auth.policy;

import com.avandocmsg.messenger.common.json.MessengerJson;
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
import java.util.UUID;

/** Sync LDAP user federation and OIDC/SAML identity brokers into Keycloak Admin API. */
public class KeycloakAuthSyncClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthSyncClient.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String ADMIN_TOKEN_UNAVAILABLE = "keycloak_admin_token_unavailable";
    private static final String JSON_PROVIDER_ID = "providerId";
    private static final String JSON_CONFIG = "config";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

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
            return new ApplyResult(false, null, ADMIN_TOKEN_UNAVAILABLE);
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
                var mapperError = maybeApplyAdminGroupMapper(existingId.get(), settings);
                if (mapperError != null) {
                    return new ApplyResult(false, existingId.get(), mapperError);
                }
                return resultFromCode(code, existingId.get(), "ldap_upsert_failed");
            }
            if (code == 201 || code == 200 || code == 204) {
                var id = findUserStorageId(token, name).orElse(null);
                var mapperError = maybeApplyAdminGroupMapper(id, settings);
                if (mapperError != null) {
                    return new ApplyResult(false, id, mapperError);
                }
                return new ApplyResult(true, id, null);
            }
            return new ApplyResult(false, null, "ldap_upsert_http_" + code);
        } catch (Exception e) {
            log.warn("upsertLdap failed: {}", e.getMessage());
            return new ApplyResult(false, null, e.getMessage());
        }
    }

    /** Maps an LDAP admin group DN to a Keycloak realm role via role-ldap-mapper. */
    public ApplyResult ldapAdminGroupMapper(UUID orgId, String ldapComponentId, String groupDn, String role) {
        if (orgId == null || ldapComponentId == null || ldapComponentId.isBlank()) {
            return new ApplyResult(false, null, "ldap_admin_mapper_missing_context");
        }
        if (groupDn == null || groupDn.isBlank()) {
            return new ApplyResult(false, null, "admin_group_dn_required");
        }
        var realmRole = role != null && !role.isBlank() ? role : "admin";
        var token = adminToken();
        if (token == null) {
            return new ApplyResult(false, null, ADMIN_TOKEN_UNAVAILABLE);
        }
        try {
            var mapperName = "admin-group-mapper-" + orgId;
            var body = adminGroupMapperBody(mapperName, ldapComponentId, groupDn, realmRole);
            var base = appConfig.keycloakAdminRealmBase() + "/components";
            var existingId = findComponentId(token, ldapComponentId, mapperName);
            int code;
            if (existingId.isPresent()) {
                code = put(base + "/" + existingId.get(), token, body);
            } else {
                code = post(base, token, body);
            }
            return resultFromCode(code, existingId.orElse(mapperName), "ldap_admin_group_mapper_failed");
        } catch (Exception e) {
            log.warn("ldapAdminGroupMapper failed orgId={}: {}", orgId, e.getMessage());
            return new ApplyResult(false, null, e.getMessage());
        }
    }

    private String maybeApplyAdminGroupMapper(String ldapComponentId, Map<String, String> settings) {
        var groupDn = settings.get("admin_group_dn");
        if (groupDn == null || groupDn.isBlank()) {
            return null;
        }
        var orgIdRaw = settings.get("org_id");
        if (orgIdRaw == null || orgIdRaw.isBlank()) {
            return "admin_group_dn_requires_org_id";
        }
        UUID orgId;
        try {
            orgId = UUID.fromString(orgIdRaw);
        } catch (Exception e) {
            return "admin_group_dn_invalid_org_id";
        }
        var role = settings.getOrDefault("admin_group_role", "admin");
        var result = ldapAdminGroupMapper(orgId, ldapComponentId, groupDn, role);
        return result.ok() ? null : result.error();
    }

    public ApplyResult upsertIdentityProvider(String alias, String providerId, Map<String, String> settings) {
        var token = adminToken();
        if (token == null) {
            return new ApplyResult(false, null, ADMIN_TOKEN_UNAVAILABLE);
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
        root.put(JSON_PROVIDER_ID, "ldap");
        root.put("providerType", "org.keycloak.storage.UserStorageProvider");
        root.set(JSON_CONFIG, config);
        return root;
    }

    private ObjectNode adminGroupMapperBody(String name, String ldapComponentId, String groupDn, String role) {
        var config = MAPPER.createObjectNode();
        putConfig(config, "mode", "LDAP_ONLY");
        putConfig(config, "user.roles.retrieve.strategy", "LOAD_ROLES_BY_MEMBER_ATTRIBUTE");
        putConfig(config, "memberof.ldap.attribute", "memberOf");
        putConfig(config, "roles.dn", groupDn);
        putConfig(config, "role.name", role);
        putConfig(config, "role.object.classes", "group");
        putConfig(config, "membership.ldap.attribute", "member");
        putConfig(config, "membership.attribute.type", "DN");
        putConfig(config, "role.name.ldap.attribute", "cn");
        putConfig(config, "use.realm.roles.mapping", "true");

        var root = MAPPER.createObjectNode();
        root.put("name", name);
        root.put(JSON_PROVIDER_ID, "role-ldap-mapper");
        root.put("providerType", "org.keycloak.storage.ldap.mappers.LDAPStorageMapper");
        root.put("parentId", ldapComponentId);
        root.set(JSON_CONFIG, config);
        return root;
    }

    private ObjectNode idpBody(String alias, String providerId, Map<String, String> s) {
        var root = MAPPER.createObjectNode();
        root.put("alias", alias);
        root.put("displayName", s.getOrDefault("display_name", alias));
        root.put(JSON_PROVIDER_ID, providerId);
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
        root.set(JSON_CONFIG, config);
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
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
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
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
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

    private Optional<String> findComponentId(String token, String parentId, String name) throws Exception {
        var url = appConfig.keycloakAdminRealmBase() + "/components?parent=" + urlEncode(parentId);
        var req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("admin token interrupted: {}", e.getMessage());
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
