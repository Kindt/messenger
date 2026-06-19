package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrganizationJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for organization JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcOrganizationJdbcRepository}.
 */
public class OrganizationRepository {
    private final JdbcOrganizationJdbcRepository jdbc;

    public OrganizationRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcOrganizationJdbcRepository(dataSource, clock, uuidGenerator);
    }

    public JdbcOrganizationJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public OrgRow create(String name) {
        var row = jdbc.create(name);
        return row == null ? null : map(row);
    }

    public boolean exists(UUID id) {
        return jdbc.exists(id);
    }

    public Optional<OrgRow> findById(UUID id) {
        return jdbc.findById(id).map(OrganizationRepository::map);
    }

    public boolean deleteIfUnused(UUID orgId) {
        return jdbc.deleteIfUnused(orgId);
    }

    public List<OrgRow> listAll() {
        return jdbc.listAll().stream().map(OrganizationRepository::map).toList();
    }

    public boolean setUserOrg(UUID userId, UUID orgId) {
        return jdbc.setUserOrg(userId, orgId);
    }

    public Optional<OrgRow> findBySlug(String slug) {
        return jdbc.findBySlug(slug).map(OrganizationRepository::map);
    }

    public Optional<OrgRow> findSingle() {
        return jdbc.findSingle().map(OrganizationRepository::map);
    }

    public boolean setSlug(UUID orgId, String slug) {
        return jdbc.setSlug(orgId, slug);
    }

    public record OrgRow(String id, String name, String slug, Instant createdAt) {}

    private static OrgRow map(JdbcOrganizationJdbcRepository.OrgRow row) {
        return new OrgRow(row.id(), row.name(), row.slug(), row.createdAt());
    }
}
