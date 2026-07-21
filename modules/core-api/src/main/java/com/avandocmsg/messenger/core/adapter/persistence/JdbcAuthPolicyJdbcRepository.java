package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.auth.policy.AuthProviderEntry;
import com.avandocmsg.messenger.api.auth.policy.OrgAuthPolicyRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAuthPolicyJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcAuthPolicyJdbcRepository.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final TypeReference<List<AuthProviderEntry>> PROVIDERS_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;

    public JdbcAuthPolicyJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<OrgAuthPolicyRow> findByOrgId(UUID orgId) {
        var sql = """
            SELECT org_id, allow_local_password, allow_self_registration, providers_json,
                   last_apply_status, last_apply_error, updated_at, updated_by
            FROM org_auth_policy WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("find auth policy failed orgId={}", orgId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public OrgAuthPolicyRow upsert(OrgAuthPolicyRow row) {
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            if (JdbcDialect.isPostgres(conn)) {
                return upsertOnConflict(conn, row);
            }
            return upsertLegacy(conn, row);
        } catch (Exception e) {
            log.error("upsert auth policy failed orgId={}", row.orgId(), e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private OrgAuthPolicyRow upsertOnConflict(java.sql.Connection conn, OrgAuthPolicyRow row) throws Exception {
        var sql = """
            INSERT INTO org_auth_policy (
                org_id, allow_local_password, allow_self_registration, providers_json,
                last_apply_status, last_apply_error, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
            ON CONFLICT (org_id) DO UPDATE SET
                allow_local_password = EXCLUDED.allow_local_password,
                allow_self_registration = EXCLUDED.allow_self_registration,
                providers_json = EXCLUDED.providers_json,
                last_apply_status = EXCLUDED.last_apply_status,
                last_apply_error = EXCLUDED.last_apply_error,
                updated_at = now(),
                updated_by = EXCLUDED.updated_by
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, row.orgId());
            stmt.setBoolean(2, row.allowLocalPassword());
            stmt.setBoolean(3, row.allowSelfRegistration());
            stmt.setString(4, providersToJson(row.providers()));
            stmt.setString(5, row.lastApplyStatus());
            stmt.setString(6, row.lastApplyError());
            stmt.setObject(7, row.updatedBy());
            stmt.executeUpdate();
            return findByOrgId(row.orgId()).orElse(row);
        }
    }

    private OrgAuthPolicyRow upsertLegacy(java.sql.Connection conn, OrgAuthPolicyRow row) throws Exception {
        var updateSql = """
            UPDATE org_auth_policy SET
                allow_local_password = ?,
                allow_self_registration = ?,
                providers_json = ?,
                last_apply_status = ?,
                last_apply_error = ?,
                updated_at = now(),
                updated_by = ?
            WHERE org_id = ?
            """;
        try (var stmt = conn.prepareStatement(updateSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setBoolean(1, row.allowLocalPassword());
            stmt.setBoolean(2, row.allowSelfRegistration());
            stmt.setString(3, providersToJson(row.providers()));
            stmt.setString(4, row.lastApplyStatus());
            stmt.setString(5, row.lastApplyError());
            stmt.setObject(6, row.updatedBy());
            stmt.setObject(7, row.orgId());
            if (stmt.executeUpdate() > 0) {
                return findByOrgId(row.orgId()).orElse(row);
            }
        }
        var insertSql = """
            INSERT INTO org_auth_policy (
                org_id, allow_local_password, allow_self_registration, providers_json,
                last_apply_status, last_apply_error, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
            """;
        try (var stmt = conn.prepareStatement(insertSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, row.orgId());
            stmt.setBoolean(2, row.allowLocalPassword());
            stmt.setBoolean(3, row.allowSelfRegistration());
            stmt.setString(4, providersToJson(row.providers()));
            stmt.setString(5, row.lastApplyStatus());
            stmt.setString(6, row.lastApplyError());
            stmt.setObject(7, row.updatedBy());
            stmt.executeUpdate();
            return findByOrgId(row.orgId()).orElse(row);
        }
    }

    public void updateApplyStatus(UUID orgId, String status, String error) {
        var sql = """
            UPDATE org_auth_policy
            SET last_apply_status = ?, last_apply_error = ?, updated_at = now()
            WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, status);
            stmt.setString(2, error);
            stmt.setObject(3, orgId);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("update apply status failed orgId={}", orgId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private OrgAuthPolicyRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrgAuthPolicyRow(
            rs.getObject("org_id", UUID.class),
            rs.getBoolean("allow_local_password"),
            rs.getBoolean("allow_self_registration"),
            parseProviders(rs.getString("providers_json")),
            rs.getString("last_apply_status"),
            rs.getString("last_apply_error"),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getObject("updated_by", UUID.class));
    }

    public static List<AuthProviderEntry> parseProviders(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var list = MAPPER.readValue(json, PROVIDERS_TYPE);
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("parse providers_json failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public static String providersToJson(List<AuthProviderEntry> providers) {
        try {
            return MAPPER.writeValueAsString(providers != null ? providers : List.of());
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }
}
