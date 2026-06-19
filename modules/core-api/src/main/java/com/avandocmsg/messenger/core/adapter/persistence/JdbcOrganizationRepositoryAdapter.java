package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link OrganizationRepositoryPort}. */
public final class JdbcOrganizationRepositoryAdapter implements OrganizationRepositoryPort {
    private final JdbcOrganizationJdbcRepository jdbc;

    public JdbcOrganizationRepositoryAdapter(JdbcOrganizationJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcOrganizationRepositoryAdapter(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcOrganizationJdbcRepository(dataSource, Clock.systemUTC(), uuidGenerator);
    }

    public JdbcOrganizationRepositoryAdapter(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcOrganizationJdbcRepository(dataSource, clock, uuidGenerator);
    }

    @Override
    public boolean exists(OrganizationId id) {
        return id != null && jdbc.exists(id.value());
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.findById(id.value()).map(JdbcOrganizationRepositoryAdapter::mapRow);
    }

    @Override
    public List<Organization> listAll() {
        return jdbc.listAll().stream().map(JdbcOrganizationRepositoryAdapter::mapRow).toList();
    }

    @Override
    public Optional<Organization> create(String name) {
        var row = jdbc.create(name);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(mapRow(row));
    }

    @Override
    public boolean deleteIfUnused(OrganizationId orgId) {
        return orgId != null && jdbc.deleteIfUnused(orgId.value());
    }

    @Override
    public boolean setUserOrg(UserId userId, OrganizationId orgId) {
        if (userId == null || orgId == null) {
            return false;
        }
        return jdbc.setUserOrg(userId.value(), orgId.value());
    }

    private static Organization mapRow(JdbcOrganizationJdbcRepository.OrgRow row) {
        return new Organization(
            OrganizationId.of(UUID.fromString(row.id())),
            row.name(),
            row.createdAt());
    }
}
