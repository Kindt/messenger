package com.avandocmsg.messenger.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public class OrgIpAllowlistRepository {
    private static final Logger log = LoggerFactory.getLogger(OrgIpAllowlistRepository.class);

    private final DataSource dataSource;

    public OrgIpAllowlistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Row(UUID orgId, boolean enabled, String allowedCidrs) {}

    public Optional<Row> findByOrgId(UUID orgId) {
        var sql = "SELECT org_id, enabled, allowed_cidrs FROM org_ip_allowlist WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Row(
                        rs.getObject("org_id", UUID.class),
                        rs.getBoolean("enabled"),
                        rs.getString("allowed_cidrs")));
                }
            }
        } catch (Exception e) {
            log.error("org ip allowlist find failed org={}", orgId, e);
        }
        return Optional.empty();
    }

    public Row upsert(UUID orgId, boolean enabled, String allowedCidrs) {
        var sql = """
            INSERT INTO org_ip_allowlist (org_id, enabled, allowed_cidrs, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (org_id) DO UPDATE SET
              enabled = EXCLUDED.enabled,
              allowed_cidrs = EXCLUDED.allowed_cidrs,
              updated_at = now()
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setBoolean(2, enabled);
            stmt.setString(3, allowedCidrs != null ? allowedCidrs : "");
            stmt.executeUpdate();
            return new Row(orgId, enabled, allowedCidrs != null ? allowedCidrs : "");
        } catch (Exception e) {
            log.error("org ip allowlist upsert failed org={}", orgId, e);
            return new Row(orgId, false, "");
        }
    }
}
