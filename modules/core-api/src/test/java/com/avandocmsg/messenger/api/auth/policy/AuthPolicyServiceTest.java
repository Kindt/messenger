package com.avandocmsg.messenger.api.auth.policy;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyServiceTest {

    private UUID orgId;
    private InMemoryAuthPolicyRepository policyRepo;
    private InMemoryOrgRepository orgRepo;
    private AuthPolicyService service;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        policyRepo = new InMemoryAuthPolicyRepository();
        orgRepo = new InMemoryOrgRepository();
        orgRepo.rows.put(orgId, new OrganizationRepository.OrgRow(orgId.toString(), "Acme", "acme", Instant.EPOCH));
        var props = new Properties();
        props.setProperty("keycloak.issuer", "http://kc:8080/realms/avandocmsg");
        props.setProperty("web.public.base.url", "http://ui/");
        var appConfig = new AppConfig() {
            @Override
            public String keycloakIssuer() {
                return "http://kc:8080/realms/avandocmsg";
            }

            @Override
            public String webPublicBaseUrl() {
                return "http://ui/";
            }

            @Override
            public Optional<UUID> defaultOrgId() {
                return Optional.empty();
            }
        };
        service = new AuthPolicyService(appConfig, policyRepo, orgRepo, new KeycloakAuthSyncClient(appConfig) {
            @Override
            public ApplyResult upsertLdap(String name, java.util.Map<String, String> settings) {
                return new ApplyResult(true, "kc-ldap-id", null);
            }
        });
    }

    @Test
    void loginOptions_resolvesOrgBySlug() {
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(), null, null, Instant.EPOCH, null);
        var opts = service.loginOptions("localhost", "acme", "http://ui/").orElseThrow();
        assertEquals(orgId.toString(), opts.orgId());
        assertEquals("acme", opts.orgSlug());
        assertEquals(1, opts.methods().size());
        assertEquals("password", opts.methods().get(0).type());
    }

    @Test
    void loginOptions_includesOidcBrokerUrl() {
        var oidc = new AuthProviderEntry(
            "oidc1", "oidc", "corp-sso", "Corp SSO", 1, true, null, "applied", null,
            java.util.Map.of("client_id", "x", "discovery_url", "http://idp/.well-known"));
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(oidc), "ok", null, Instant.EPOCH, null);
        var opts = service.loginOptions("localhost", "acme", "http://ui/").orElseThrow();
        assertEquals(2, opts.methods().size());
        var broker = opts.methods().stream().filter(m -> "oidc".equals(m.type())).findFirst().orElseThrow();
        assertTrue(broker.authorizationUrl().contains("kc_idp_hint=corp-sso"));
    }

    @Test
    void testPolicy_orgNotFound() {
        var missing = UUID.randomUUID();
        assertTrue(service.testPolicy(missing, null).isEmpty());
    }

    @Test
    void testPolicy_providerNotFoundWhenEmpty() {
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(), null, null, Instant.EPOCH, null);
        var result = service.testPolicy(orgId, null).orElseThrow();
        assertFalse(result.ok());
        assertEquals("provider_not_found", result.message());
    }

    @Test
    void testPolicy_nonLdapProviderRejected() {
        var oidc = new AuthProviderEntry(
            "oidc1", "oidc", "corp-sso", "Corp SSO", 1, true, null, "applied", null,
            java.util.Map.of("client_id", "x"));
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(oidc), "ok", null, Instant.EPOCH, null);
        var result = service.testPolicy(orgId, "oidc1").orElseThrow();
        assertFalse(result.ok());
        assertEquals("test_supported_for_ldap_only", result.message());
    }

    @Test
    void testPolicy_ldapMissingConnectionUrl() {
        var ldap = new AuthProviderEntry(
            "ldap1", "ldap", "corp-ldap", "Corp LDAP", 0, true, null, "draft", null,
            java.util.Map.of("bind_dn", "cn=admin"));
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(ldap), "ok", null, Instant.EPOCH, null);
        var result = service.testPolicy(orgId, "ldap1").orElseThrow();
        assertFalse(result.ok());
        assertEquals("connection_url_required", result.message());
    }

    @Test
    void testPolicy_ldapTcpUnreachable() {
        var ldap = new AuthProviderEntry(
            "ldap1", "ldap", "corp-ldap", "Corp LDAP", 0, true, null, "draft", null,
            java.util.Map.of("connection_url", "ldap://127.0.0.1:1"));
        policyRepo.store = new OrgAuthPolicyRow(orgId, true, false, List.of(ldap), "ok", null, Instant.EPOCH, null);
        var result = service.testPolicy(orgId, null).orElseThrow();
        assertFalse(result.ok());
        assertTrue(result.message().startsWith("tcp_connect_failed"));
    }

    static final class InMemoryAuthPolicyRepository extends AuthPolicyRepository {
        OrgAuthPolicyRow store;

        InMemoryAuthPolicyRepository() {
            super(null);
        }

        @Override
        public Optional<OrgAuthPolicyRow> findByOrgId(UUID orgId) {
            return store != null && store.orgId().equals(orgId) ? Optional.of(store) : Optional.empty();
        }

        @Override
        public OrgAuthPolicyRow upsert(OrgAuthPolicyRow row) {
            store = row;
            return row;
        }
    }

    static final class InMemoryOrgRepository extends OrganizationRepository {
        final java.util.Map<UUID, OrganizationRepository.OrgRow> rows = new java.util.HashMap<>();

        InMemoryOrgRepository() {
            super(null, null, null);
        }

        @Override
        public boolean exists(UUID id) {
            return rows.containsKey(id);
        }

        @Override
        public Optional<OrganizationRepository.OrgRow> findBySlug(String slug) {
            return rows.values().stream()
                .filter(r -> slug.equalsIgnoreCase(r.slug()))
                .findFirst();
        }

        @Override
        public Optional<OrganizationRepository.OrgRow> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }
    }
}
