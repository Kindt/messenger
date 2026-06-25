package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link ScimGroupRepositoryPort}. */
public final class JdbcScimGroupRepositoryAdapter implements ScimGroupRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcScimGroupRepositoryAdapter.class);

    private final DataSource dataSource;

    public JdbcScimGroupRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ScimGroupRow> findById(UUID id) {
        var sql = """
            SELECT id, org_id, display_name, external_id, members_json, created_at, updated_at
            FROM scim_groups WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("findById failed id={}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<ScimGroupRow> listByOrg(UUID orgId, int offset, int limit) {
        var sql = """
            SELECT id, org_id, display_name, external_id, members_json, created_at, updated_at
            FROM scim_groups WHERE org_id = ?
            ORDER BY display_name
            OFFSET ? LIMIT ?
            """;
        var out = new ArrayList<ScimGroupRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("listByOrg failed orgId={}", orgId, e);
        }
        return out;
    }

    @Override
    public int countByOrg(UUID orgId) {
        var sql = "SELECT COUNT(*) FROM scim_groups WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            log.error("countByOrg failed orgId={}", orgId, e);
            return 0;
        }
    }

    @Override
    public boolean insert(UUID id, UUID orgId, String displayName, String externalId, String membersJson) {
        var sql = """
            INSERT INTO scim_groups (id, org_id, display_name, external_id, members_json)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setObject(2, orgId);
            stmt.setString(3, displayName);
            stmt.setString(4, externalId);
            stmt.setString(5, membersJson != null ? membersJson : "[]");
            return stmt.executeUpdate() == 1;
        } catch (Exception e) {
            log.error("insert failed id={}", id, e);
            return false;
        }
    }

    @Override
    public boolean update(UUID id, String displayName, String externalId, String membersJson) {
        var sql = """
            UPDATE scim_groups
            SET display_name = ?, external_id = ?, members_json = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, displayName);
            stmt.setString(2, externalId);
            stmt.setString(3, membersJson != null ? membersJson : "[]");
            stmt.setObject(4, id);
            return stmt.executeUpdate() == 1;
        } catch (Exception e) {
            log.error("update failed id={}", id, e);
            return false;
        }
    }

    @Override
    public boolean delete(UUID id) {
        var sql = "DELETE FROM scim_groups WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            return stmt.executeUpdate() == 1;
        } catch (Exception e) {
            log.error("delete failed id={}", id, e);
            return false;
        }
    }

    private static ScimGroupRow mapRow(java.sql.ResultSet rs) throws Exception {
        return new ScimGroupRow(
            rs.getObject("id", UUID.class),
            rs.getObject("org_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("external_id"),
            rs.getString("members_json"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }
}
