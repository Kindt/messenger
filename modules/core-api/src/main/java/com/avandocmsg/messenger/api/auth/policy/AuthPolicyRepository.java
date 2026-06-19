package com.avandocmsg.messenger.api.auth.policy;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuthPolicyJdbcRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for auth policy JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcAuthPolicyJdbcRepository}.
 */
public class AuthPolicyRepository {
    private final JdbcAuthPolicyJdbcRepository jdbc;

    public AuthPolicyRepository(DataSource dataSource) {
        this.jdbc = new JdbcAuthPolicyJdbcRepository(dataSource);
    }

    public JdbcAuthPolicyJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public Optional<OrgAuthPolicyRow> findByOrgId(UUID orgId) {
        return jdbc.findByOrgId(orgId);
    }

    public OrgAuthPolicyRow defaultPolicy(UUID orgId) {
        return new OrgAuthPolicyRow(orgId, true, false, List.of(), null, null, Instant.EPOCH, null);
    }

    public OrgAuthPolicyRow upsert(OrgAuthPolicyRow row) {
        return jdbc.upsert(row);
    }

    public void updateApplyStatus(UUID orgId, String status, String error) {
        jdbc.updateApplyStatus(orgId, status, error);
    }

    static List<AuthProviderEntry> parseProviders(String json) {
        return JdbcAuthPolicyJdbcRepository.parseProviders(json);
    }

    static String providersToJson(List<AuthProviderEntry> providers) {
        return JdbcAuthPolicyJdbcRepository.providersToJson(providers);
    }
}
