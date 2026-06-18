package com.avandocmsg.messenger.api.auth.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuthPolicyRepository {
    private static final Logger log = LoggerFactory.getLogger(AuthPolicyRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<AuthProviderEntry>> PROVIDERS_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;

    public AuthPolicyRepository(DataSource dataSource) {
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
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("find auth policy failed orgId={}", orgId, e);
            return Optional.empty();
        }
    }

    public OrgAuthPolicyRow defaultPolicy(UUID orgId) {
        return new OrgAuthPolicyRow(orgId, true, false, List.of(), null, null, Instant.EPOCH, null);
    }

    public OrgAuthPolicyRow upsert(OrgAuthPolicyRow row) {
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
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(updateSql)) {
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
        } catch (Exception e) {
            log.error("update auth policy failed orgId={}", row.orgId(), e);
            return row;
        }

        var insertSql = """
            INSERT INTO org_auth_policy (
                org_id, allow_local_password, allow_self_registration, providers_json,
                last_apply_status, last_apply_error, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(insertSql)) {
            stmt.setObject(1, row.orgId());
            stmt.setBoolean(2, row.allowLocalPassword());
            stmt.setBoolean(3, row.allowSelfRegistration());
            stmt.setString(4, providersToJson(row.providers()));
            stmt.setString(5, row.lastApplyStatus());
            stmt.setString(6, row.lastApplyError());
            stmt.setObject(7, row.updatedBy());
            stmt.executeUpdate();
            return findByOrgId(row.orgId()).orElse(row);
        } catch (Exception e) {
            log.error("insert auth policy failed orgId={}", row.orgId(), e);
            return row;
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
            stmt.setString(1, status);
            stmt.setString(2, error);
            stmt.setObject(3, orgId);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("update apply status failed orgId={}", orgId, e);
        }
    }

    private OrgAuthPolicyRow mapRow(java.sql.ResultSet rs) throws Exception {
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

    static List<AuthProviderEntry> parseProviders(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var list = MAPPER.readValue(json, PROVIDERS_TYPE);
            return list != null ? list : List.of();
        } catch (Exception e) {
            log.warn("parse providers_json failed: {}", e.getMessage());
            return List.of();
        }
    }

    static String providersToJson(List<AuthProviderEntry> providers) {
        try {
            return MAPPER.writeValueAsString(providers != null ? providers : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }
}
