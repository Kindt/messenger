package com.avandocmsg.messenger.api.directory;

import com.avandocmsg.messenger.api.auth.policy.AuthPolicyRepository;
import com.avandocmsg.messenger.api.auth.policy.AuthProviderEntry;
import com.avandocmsg.messenger.api.auth.policy.OrgAuthPolicyRow;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcDirectorySyncRunRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrgUserDirectoryAdapter;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DirectorySyncServiceH2Test {

    private HikariDataSource ds;
    private UUID orgId;
    private DirectorySyncService service;
    private StubLdapClient ldapClient;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dirsync_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE organizations (
                  id UUID PRIMARY KEY,
                  name VARCHAR(256) NOT NULL,
                  slug VARCHAR(64),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE org_auth_policy (
                  org_id UUID PRIMARY KEY,
                  allow_local_password BOOLEAN NOT NULL DEFAULT TRUE,
                  allow_self_registration BOOLEAN NOT NULL DEFAULT FALSE,
                  providers_json VARCHAR(10000) NOT NULL DEFAULT '[]',
                  last_apply_status VARCHAR(32),
                  last_apply_error VARCHAR(2000),
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_by UUID
                )
                """);
            st.execute("""
                CREATE TABLE directory_sync_runs (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  users_upserted INT NOT NULL DEFAULT 0,
                  error VARCHAR(2000),
                  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  finished_at TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL UNIQUE,
                  display_name VARCHAR(128) NOT NULL,
                  email VARCHAR(256),
                  external_id VARCHAR(256),
                  phone VARCHAR(20),
                  hidden BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
                  last_seen_at TIMESTAMP,
                  org_id UUID,
                  privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false,
                  ui_locale VARCHAR(8)
                )
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Sync Org')");
        }

        var authPolicyRepository = new AuthPolicyRepository(ds);
        var orgRepo = new OrganizationRepository(ds, java.time.Clock.systemUTC(), UuidGenerator.standard());
        var runRepo = new JdbcDirectorySyncRunRepositoryAdapter(new DirectorySyncRunRepository(ds));
        var userRepo = new JdbcOrgUserDirectoryAdapter(new UserRepository(ds));
        ldapClient = new StubLdapClient();
        service = new DirectorySyncService(
            authPolicyRepository, orgRepo, runRepo, userRepo, ldapClient, UuidGenerator.standard());

        var ldapProvider = new AuthProviderEntry(
            "ldap1", "ldap", "corp", "Corp LDAP", 0, true, null, "applied", null,
            Map.of(
                "connection_url", "ldap://mock:389",
                "users_dn", "ou=users,dc=corp",
                "bind_dn", "cn=admin,dc=corp",
                "bind_password", "secret"));
        authPolicyRepository.upsert(new OrgAuthPolicyRow(
            orgId, true, false, List.of(ldapProvider), "ok", null, Instant.EPOCH, null));

        ldapClient.entries = List.of(
            new LdapUserEntry("ext-1", "alice", "alice@corp.test", "Alice"),
            new LdapUserEntry("ext-2", "bob", "bob@corp.test", "Bob"));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void syncFromLdap_upsertsUsersAndRecordsRun() {
        var run = service.syncFromLdap(orgId).orElseThrow();
        assertEquals("ok", run.status());
        assertEquals(2, run.usersUpserted());
        assertNotNull(run.finishedAt());

        var alice = new UserRepository(ds).findByOrgAndExternalId(orgId, "ext-1").orElseThrow();
        assertEquals("alice", alice.username());
        assertEquals("alice@corp.test", alice.email());

        var status = service.latestStatus(orgId).orElseThrow();
        assertEquals(run.id(), status.id());
    }

    @Test
    void syncFromLdap_withoutLdapProvider_recordsError() throws Exception {
        var otherOrg = UUID.randomUUID();
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + otherOrg + "', 'No LDAP')");
        }
        var run = service.syncFromLdap(otherOrg).orElseThrow();
        assertEquals("error", run.status());
        assertEquals("no_enabled_ldap_provider", run.error());
    }

    static final class StubLdapClient implements LdapDirectoryClient {
        List<LdapUserEntry> entries = List.of();

        @Override
        public List<LdapUserEntry> searchUsers(Map<String, String> settings) {
            assertEquals("ldap://mock:389", settings.get("connection_url"));
            return entries;
        }
    }
}
