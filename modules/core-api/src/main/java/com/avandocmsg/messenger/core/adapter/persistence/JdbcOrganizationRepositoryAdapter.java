package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link OrganizationRepositoryPort} (organization read). */
public final class JdbcOrganizationRepositoryAdapter implements OrganizationRepositoryPort {
    private final DataSource dataSource;

    public JdbcOrganizationRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(OrganizationId id) {
        if (dataSource == null) {
            return false;
        }
        var sql = "SELECT 1 FROM organizations WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = "SELECT id, name, created_at FROM organizations WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Organization(
                        OrganizationId.of(rs.getObject("id", UUID.class)),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
