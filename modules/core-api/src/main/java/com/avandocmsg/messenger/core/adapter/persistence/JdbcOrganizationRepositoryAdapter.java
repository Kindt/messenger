package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link OrganizationRepositoryPort}. */
public final class JdbcOrganizationRepositoryAdapter implements OrganizationRepositoryPort {
    private final DataSource dataSource;
    private final UuidGenerator uuidGenerator;

    public JdbcOrganizationRepositoryAdapter(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.uuidGenerator = uuidGenerator;
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
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public List<Organization> listAll() {
        if (dataSource == null) {
            return List.of();
        }
        var sql = "SELECT id, name, created_at FROM organizations ORDER BY name";
        var out = new ArrayList<Organization>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.add(mapRow(rs));
            }
        } catch (Exception e) {
            return List.of();
        }
        return out;
    }

    @Override
    public Optional<Organization> create(String name) {
        if (dataSource == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        var id = OrganizationId.of(uuidGenerator.randomUuid());
        var trimmed = name.trim();
        var sql = "INSERT INTO organizations (id, name, created_at) VALUES (?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            stmt.setString(2, trimmed);
            stmt.executeUpdate();
            return findById(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteIfUnused(OrganizationId orgId) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            DELETE FROM organizations o
            WHERE o.id = ?
              AND NOT EXISTS (SELECT 1 FROM users u WHERE u.org_id = o.id)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean setUserOrg(UserId userId, OrganizationId orgId) {
        if (dataSource == null) {
            return false;
        }
        var sql = "UPDATE users SET org_id = ?, updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId.value());
            stmt.setObject(2, userId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Organization mapRow(java.sql.ResultSet rs) throws Exception {
        return new Organization(
            OrganizationId.of(rs.getObject("id", UUID.class)),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant());
    }
}
