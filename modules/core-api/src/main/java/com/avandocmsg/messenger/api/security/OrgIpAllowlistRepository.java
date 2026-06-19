package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrgIpAllowlistJdbcRepository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for org IP allowlist JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcOrgIpAllowlistJdbcRepository}.
 */
public class OrgIpAllowlistRepository {
    private final JdbcOrgIpAllowlistJdbcRepository jdbc;

    public OrgIpAllowlistRepository(DataSource dataSource) {
        this.jdbc = new JdbcOrgIpAllowlistJdbcRepository(dataSource);
    }

    public JdbcOrgIpAllowlistJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public record Row(UUID orgId, boolean enabled, String allowedCidrs) {}

    public Optional<Row> findByOrgId(UUID orgId) {
        return jdbc.findByOrgId(orgId);
    }

    public Row upsert(UUID orgId, boolean enabled, String allowedCidrs) {
        return jdbc.upsert(orgId, enabled, allowedCidrs);
    }
}
