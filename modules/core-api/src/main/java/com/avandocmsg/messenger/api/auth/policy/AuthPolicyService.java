package com.avandocmsg.messenger.api.auth.policy;

import com.avandocmsg.messenger.api.auth.dto.AuthPolicyResponse;
import com.avandocmsg.messenger.api.auth.dto.LoginOptionsResponse;
import com.avandocmsg.messenger.api.auth.dto.UpdateAuthPolicyRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AuthPolicyService {
    private static final Logger log = LoggerFactory.getLogger(AuthPolicyService.class);

    private final AppConfig appConfig;
    private final AuthPolicyRepository authPolicyRepository;
    private final OrganizationRepository organizationRepository;
    private final KeycloakAuthSyncClient keycloakAuthSyncClient;

    public AuthPolicyService(
        AppConfig appConfig,
        AuthPolicyRepository authPolicyRepository,
        OrganizationRepository organizationRepository,
        KeycloakAuthSyncClient keycloakAuthSyncClient
    ) {
        this.appConfig = appConfig;
        this.authPolicyRepository = authPolicyRepository;
        this.organizationRepository = organizationRepository;
        this.keycloakAuthSyncClient = keycloakAuthSyncClient;
    }

    public Optional<LoginOptionsResponse> loginOptions(String hostHeader, String orgSlugParam, String redirectBase) {
        return resolveOrg(hostHeader, orgSlugParam).map(org -> buildLoginOptions(org, redirectBase));
    }

    public Optional<AuthPolicyResponse> getPolicy(UUID orgId) {
        if (!organizationRepository.exists(orgId)) {
            return Optional.empty();
        }
        var row = authPolicyRepository.findByOrgId(orgId).orElseGet(() -> authPolicyRepository.defaultPolicy(orgId));
        return Optional.of(toResponse(orgId, row, true));
    }

    public Optional<AuthPolicyResponse> updatePolicy(UUID orgId, UpdateAuthPolicyRequest request, UUID actorId) {
        if (!organizationRepository.exists(orgId)) {
            return Optional.empty();
        }
        var current = authPolicyRepository.findByOrgId(orgId).orElseGet(() -> authPolicyRepository.defaultPolicy(orgId));
        var allowLocal = request.allowLocalPassword() != null ? request.allowLocalPassword() : current.allowLocalPassword();
        var allowReg = request.allowSelfRegistration() != null
            ? request.allowSelfRegistration()
            : current.allowSelfRegistration();
        var providers = request.providers() != null ? request.providers() : current.providers();

        var normalized = normalizeProviders(providers);
        var apply = request.applyToKeycloak() == null || request.applyToKeycloak();
        var applied = normalized;
        String applyStatus = current.lastApplyStatus();
        String applyError = current.lastApplyError();
        if (apply) {
            var applyOutcome = applyProviders(normalized);
            applied = applyOutcome.providers();
            applyStatus = applyOutcome.status();
            applyError = applyOutcome.error();
        }

        var saved = authPolicyRepository.upsert(new OrgAuthPolicyRow(
            orgId, allowLocal, allowReg, applied, applyStatus, applyError, current.updatedAt(), actorId));
        return Optional.of(toResponse(orgId, saved, true));
    }

    private LoginOptionsResponse buildLoginOptions(OrganizationRepository.OrgRow org, String redirectBase) {
        var orgId = UUID.fromString(org.id());
        var row = authPolicyRepository.findByOrgId(orgId).orElseGet(() -> authPolicyRepository.defaultPolicy(orgId));
        var methods = new ArrayList<LoginOptionsResponse.LoginMethodJson>();

        if (row.allowLocalPassword()) {
            methods.add(new LoginOptionsResponse.LoginMethodJson("password", "password", "Password", null));
        }
        for (var provider : row.providers()) {
            if (!provider.enabled()) {
                continue;
            }
            var type = mapLoginType(provider.type());
            if (type == null) {
                continue;
            }
            if ("password".equals(type) && !row.allowLocalPassword()) {
                continue;
            }
            var label = provider.displayName() != null && !provider.displayName().isBlank()
                ? provider.displayName()
                : provider.alias();
            var authUrl = ("oidc".equals(type) || "saml".equals(type))
                ? brokerAuthorizationUrl(provider.alias(), redirectBase)
                : null;
            methods.add(new LoginOptionsResponse.LoginMethodJson(provider.id(), type, label, authUrl));
        }

        return new LoginOptionsResponse(org.id(), org.slug(), row.allowSelfRegistration(), List.copyOf(methods));
    }

    private String brokerAuthorizationUrl(String idpAlias, String redirectBase) {
        var redirect = redirectBase != null && !redirectBase.isBlank()
            ? redirectBase
            : appConfig.webPublicBaseUrl();
        var encodedRedirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        return appConfig.keycloakIssuer()
            + "/protocol/openid-connect/auth?client_id=messenger-web"
            + "&redirect_uri=" + encodedRedirect
            + "&response_type=code&scope=openid"
            + "&kc_idp_hint=" + urlEncode(idpAlias);
    }

    private Optional<OrganizationRepository.OrgRow> resolveOrg(String hostHeader, String orgSlugParam) {
        if (orgSlugParam != null && !orgSlugParam.isBlank()) {
            return organizationRepository.findBySlug(orgSlugParam.trim().toLowerCase());
        }
        var fromHost = slugFromHost(hostHeader);
        if (fromHost.isPresent()) {
            var bySlug = organizationRepository.findBySlug(fromHost.get());
            if (bySlug.isPresent()) {
                return bySlug;
            }
        }
        var defaultId = appConfig.defaultOrgId();
        if (defaultId.isPresent()) {
            return organizationRepository.findById(defaultId.get());
        }
        return organizationRepository.findSingle();
    }

    private static Optional<String> slugFromHost(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return Optional.empty();
        }
        var host = hostHeader.split(":")[0].trim().toLowerCase();
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return Optional.empty();
        }
        var parts = host.split("\\.");
        if (parts.length < 3) {
            return Optional.empty();
        }
        var sub = parts[0];
        if (sub.isBlank() || "www".equals(sub)) {
            return Optional.empty();
        }
        return Optional.of(sub);
    }

    private ApplyOutcome applyProviders(List<AuthProviderEntry> providers) {
        var updated = new ArrayList<AuthProviderEntry>();
        String lastStatus = "ok";
        String lastError = null;
        for (var p : providers) {
            if (!p.enabled()) {
                updated.add(p.withStatus("disabled"));
                continue;
            }
            try {
                var settings = resolvedSettings(p);
                KeycloakAuthSyncClient.ApplyResult result;
                if ("ldap".equalsIgnoreCase(p.type())) {
                    result = keycloakAuthSyncClient.upsertLdap(p.alias(), settings);
                } else if ("oidc".equalsIgnoreCase(p.type())) {
                    result = keycloakAuthSyncClient.upsertIdentityProvider(p.alias(), "oidc", settings);
                } else if ("saml".equalsIgnoreCase(p.type())) {
                    result = keycloakAuthSyncClient.upsertIdentityProvider(p.alias(), "saml", settings);
                } else {
                    updated.add(p.withStatus("unsupported"));
                    lastStatus = "partial";
                    lastError = "unsupported_type:" + p.type();
                    continue;
                }
                if (result.ok()) {
                    updated.add(p.withKcComponentId(result.componentId(), "applied"));
                } else {
                    updated.add(p.withStatus("error"));
                    lastStatus = "error";
                    lastError = result.error();
                }
            } catch (Exception e) {
                log.warn("apply provider {} failed: {}", p.id(), e.getMessage());
                updated.add(p.withStatus("error"));
                lastStatus = "error";
                lastError = e.getMessage();
            }
        }
        return new ApplyOutcome(List.copyOf(updated), lastStatus, lastError);
    }

    private Map<String, String> resolvedSettings(AuthProviderEntry provider) {
        var out = new HashMap<String, String>();
        if (provider.settings() != null) {
            provider.settings().forEach((k, v) -> {
                if (v != null) {
                    out.put(k, v);
                }
            });
        }
        if (provider.secretRef() != null && !provider.secretRef().isBlank()) {
            var env = System.getenv(provider.secretRef());
            if (env != null && !env.isBlank()) {
                if ("ldap".equalsIgnoreCase(provider.type())) {
                    out.putIfAbsent("bind_password", env);
                } else if ("oidc".equalsIgnoreCase(provider.type())) {
                    out.putIfAbsent("client_secret", env);
                }
            }
        }
        out.putIfAbsent("priority", String.valueOf(provider.priority()));
        if (provider.displayName() != null) {
            out.putIfAbsent("display_name", provider.displayName());
        }
        return out;
    }

    private List<AuthProviderEntry> normalizeProviders(List<AuthProviderEntry> providers) {
        if (providers == null) {
            return List.of();
        }
        var out = new ArrayList<AuthProviderEntry>();
        for (var p : providers) {
            if (p.id() == null || p.id().isBlank()) {
                continue;
            }
            var alias = p.alias() != null && !p.alias().isBlank() ? p.alias() : p.id();
            out.add(new AuthProviderEntry(
                p.id(),
                p.type(),
                alias,
                p.displayName(),
                p.priority(),
                p.enabled(),
                p.kcComponentId(),
                p.status(),
                p.secretRef(),
                p.settings()));
        }
        return List.copyOf(out);
    }

    private AuthPolicyResponse toResponse(UUID orgId, OrgAuthPolicyRow row, boolean maskSecrets) {
        var providers = maskSecrets ? maskProviders(row.providers()) : row.providers();
        return new AuthPolicyResponse(
            orgId.toString(),
            row.allowLocalPassword(),
            row.allowSelfRegistration(),
            providers,
            row.lastApplyStatus(),
            row.lastApplyError());
    }

    private List<AuthProviderEntry> maskProviders(List<AuthProviderEntry> providers) {
        var out = new ArrayList<AuthProviderEntry>();
        for (var p : providers) {
            Map<String, String> settings = p.settings();
            if (settings != null && !settings.isEmpty()) {
                var masked = new HashMap<String, String>();
                settings.forEach((k, v) -> masked.put(k, isSecretKey(k) ? "***" : v));
                out.add(new AuthProviderEntry(
                    p.id(), p.type(), p.alias(), p.displayName(), p.priority(), p.enabled(),
                    p.kcComponentId(), p.status(), p.secretRef(), Map.copyOf(masked)));
            } else {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    private static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        var k = key.toLowerCase();
        return k.contains("password") || k.contains("secret") || k.contains("credential");
    }

    private static String mapLoginType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "ldap", "password" -> "password";
            case "oidc" -> "oidc";
            case "saml" -> "saml";
            default -> null;
        };
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ApplyOutcome(List<AuthProviderEntry> providers, String status, String error) {}
}
