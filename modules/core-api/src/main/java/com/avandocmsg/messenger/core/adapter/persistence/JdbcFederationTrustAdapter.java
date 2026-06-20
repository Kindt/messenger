package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.FederationTrustPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcFederationTrustAdapter implements FederationTrustPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcFederationTrustAdapter.class);
    private final DataSource dataSource;

    public JdbcFederationTrustAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID insert(UUID orgId, UUID partnerOrgId, String status, Instant expiresAt) {
        if (orgId.equals(partnerOrgId)) {
            return null;
        }
        var id = UUID.randomUUID();
        var st = status != null ? status : "active";
        var insertSql = """
            INSERT INTO federation_trust (id, org_id, partner_org_id, status, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(insertSql)) {
            bindTrustRow(stmt, id, orgId, partnerOrgId, st, expiresAt);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            if (!isUniqueViolation(e)) {
                log.error("federation trust insert failed", e);
                return null;
            }
        } catch (Exception e) {
            log.error("federation trust insert failed", e);
            return null;
        }
        return upsertExisting(orgId, partnerOrgId, st, expiresAt);
    }

    private UUID upsertExisting(UUID orgId, UUID partnerOrgId, String status, Instant expiresAt) {
        var updateSql = """
            UPDATE federation_trust SET status = ?, expires_at = ?, updated_at = CURRENT_TIMESTAMP
            WHERE org_id = ? AND partner_org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, status);
            if (expiresAt != null) {
                stmt.setTimestamp(2, Timestamp.from(expiresAt));
            } else {
                stmt.setNull(2, java.sql.Types.TIMESTAMP);
            }
            stmt.setObject(3, orgId);
            stmt.setObject(4, partnerOrgId);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("federation trust upsert failed", e);
            return null;
        }
        return findTrustId(orgId, partnerOrgId).orElse(null);
    }

    private Optional<UUID> findTrustId(UUID orgId, UUID partnerOrgId) {
        var sql = "SELECT id FROM federation_trust WHERE org_id = ? AND partner_org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setObject(2, partnerOrgId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("federation trust find pair failed", e);
        }
        return Optional.empty();
    }

    private static void bindTrustRow(
        java.sql.PreparedStatement stmt,
        UUID id,
        UUID orgId,
        UUID partnerOrgId,
        String status,
        Instant expiresAt) throws SQLException {
        stmt.setObject(1, id);
        stmt.setObject(2, orgId);
        stmt.setObject(3, partnerOrgId);
        stmt.setString(4, status);
        if (expiresAt != null) {
            stmt.setTimestamp(5, Timestamp.from(expiresAt));
        } else {
            stmt.setNull(5, java.sql.Types.TIMESTAMP);
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        var state = e.getSQLState();
        if (state != null && (state.startsWith("23") || "23505".equals(state))) {
            return true;
        }
        var msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("unique");
    }

    @Override
    public List<TrustRow> listForOrg(UUID orgId) {
        return query(orgId, false);
    }

    @Override
    public List<TrustRow> listActiveForOrg(UUID orgId) {
        return query(orgId, true);
    }

    @Override
    public boolean isTrusted(UUID orgId, UUID partnerOrgId) {
        var sql = """
            SELECT 1 FROM federation_trust
            WHERE org_id = ? AND partner_org_id = ? AND status = 'active'
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setObject(2, partnerOrgId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("federation trust check failed", e);
            return false;
        }
    }

    @Override
    public boolean anyActiveTrust() {
        var sql = """
            SELECT 1 FROM federation_trust
            WHERE status = 'active' AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            return rs.next();
        } catch (Exception e) {
            log.error("federation trust anyActive failed", e);
            return false;
        }
    }

    @Override
    public Optional<TrustRow> findById(UUID id) {
        var sql = """
            SELECT id, org_id, partner_org_id, status, expires_at
            FROM federation_trust WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("federation trust find failed {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public boolean updateStatus(UUID id, String status) {
        var sql = "UPDATE federation_trust SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setObject(2, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("federation trust update failed {}", id, e);
            return false;
        }
    }

    private List<TrustRow> query(UUID orgId, boolean activeOnly) {
        var sql = activeOnly
            ? """
            SELECT id, org_id, partner_org_id, status, expires_at
            FROM federation_trust
            WHERE org_id = ? AND status = 'active'
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            ORDER BY created_at DESC
            """
            : """
            SELECT id, org_id, partner_org_id, status, expires_at
            FROM federation_trust
            WHERE org_id = ?
            ORDER BY created_at DESC
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<TrustRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("federation trust list failed org={}", orgId, e);
            return List.of();
        }
    }

    private static TrustRow mapRow(java.sql.ResultSet rs) throws Exception {
        var exp = rs.getTimestamp("expires_at");
        return new TrustRow(
            rs.getObject("id", UUID.class),
            rs.getObject("org_id", UUID.class),
            rs.getObject("partner_org_id", UUID.class),
            rs.getString("status"),
            exp != null ? exp.toInstant() : null);
    }
}
