package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPluginJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcPluginJdbcRepository.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final DataSource dataSource;

    public JdbcPluginJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PluginRepository.PresetRow> listPresets() {
        var sql = """
            SELECT id, plugin_class, runtime_kind, config_schema_version, capabilities::text
            FROM plugin_presets
            ORDER BY id
            """;
        var out = new ArrayList<PluginRepository.PresetRow>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(ps);
            try (var rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PluginRepository.PresetRow(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getInt(4),
                    rs.getString(5)
                ));
            }
            }
        } catch (Exception e) {
            log.warn("listPresets failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return out;
    }

    public PluginRepository.InstancePage listInstances(UUID orgId, int limit, int offset) {
        var total = countInstances(orgId);
        var sql = """
            SELECT id, org_id, preset_id, bot_name, display_name, enabled, plugin_class,
                   runtime_endpoint, config_json::text, created_at, updated_at,
                   outbound_target_chat_id, outbound_actor_user_id, outbound_token_hash
            FROM plugin_instances
            WHERE org_id = ?
            ORDER BY bot_name
            LIMIT ? OFFSET ?
            """;
        var out = new ArrayList<PluginRepository.InstanceRow>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, orgId);
            ps.setInt(2, Math.max(1, Math.min(limit, 500)));
            ps.setInt(3, Math.max(0, offset));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapInstance(rs));
                }
            }
        } catch (Exception e) {
            log.warn("listInstances failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return new PluginRepository.InstancePage(List.copyOf(out), total);
    }

    public int countInstances(UUID orgId) {
        var sql = "SELECT COUNT(*) FROM plugin_instances WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.warn("countInstances failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return 0;
    }

    public Optional<PluginRepository.InstanceRow> findInstanceByOrgAndBotName(UUID orgId, String botName) {
        if (orgId == null || botName == null || botName.isBlank()) {
            return Optional.empty();
        }
        var sql = """
            SELECT id, org_id, preset_id, bot_name, display_name, enabled, plugin_class,
                   runtime_endpoint, config_json::text, created_at, updated_at,
                   outbound_target_chat_id, outbound_actor_user_id, outbound_token_hash
            FROM plugin_instances
            WHERE org_id = ? AND bot_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, orgId);
            ps.setString(2, botName.trim());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapInstance(rs));
                }
            }
        } catch (Exception e) {
            log.warn("findInstanceByOrgAndBotName failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    public boolean setInstanceEnabled(UUID id, boolean enabled) {
        var sql = """
            UPDATE plugin_instances SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setBoolean(1, enabled);
            ps.setObject(2, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            log.warn("setInstanceEnabled failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public Optional<PluginRepository.InstanceRow> findInstance(UUID id) {
        var sql = """
            SELECT id, org_id, preset_id, bot_name, display_name, enabled, plugin_class,
                   runtime_endpoint, config_json::text, created_at, updated_at,
                   outbound_target_chat_id, outbound_actor_user_id, outbound_token_hash
            FROM plugin_instances
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapInstance(rs));
                }
            }
        } catch (Exception e) {
            log.warn("findInstance failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    public boolean insertInstance(PluginRepository.InstanceRow row) {
        var sql = """
            INSERT INTO plugin_instances
            (id, org_id, preset_id, bot_name, display_name, enabled, plugin_class, runtime_endpoint, config_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, row.id());
            ps.setObject(2, row.orgId());
            ps.setString(3, row.presetId());
            ps.setString(4, row.botName());
            ps.setString(5, row.displayName());
            ps.setBoolean(6, row.enabled());
            ps.setString(7, row.pluginClass());
            ps.setString(8, row.runtimeEndpoint());
            ps.setString(9, row.configJson() != null ? row.configJson().toString() : "{}");
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            log.warn("insertInstance failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public Optional<PluginRepository.OrgPolicyRow> findOrgPolicy(UUID orgId) {
        var sql = """
            SELECT org_id, allowed_preset_ids, llm_mode, ocr_on_prem_only, updated_at
            FROM org_plugin_policies
            WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPolicy(rs));
                }
            }
        } catch (Exception e) {
            log.warn("findOrgPolicy failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    public boolean upsertOrgPolicy(PluginRepository.OrgPolicyRow row) {
        var sql = """
            INSERT INTO org_plugin_policies (org_id, allowed_preset_ids, llm_mode, ocr_on_prem_only, updated_at)
            VALUES (?, ?::jsonb, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (org_id) DO UPDATE SET
              allowed_preset_ids = EXCLUDED.allowed_preset_ids,
              llm_mode = EXCLUDED.llm_mode,
              ocr_on_prem_only = EXCLUDED.ocr_on_prem_only,
              updated_at = CURRENT_TIMESTAMP
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, row.orgId());
            ps.setString(2, MAPPER.writeValueAsString(row.allowedPresetIds()));
            ps.setString(3, row.llmMode());
            ps.setBoolean(4, row.ocrOnPremOnly());
            return ps.executeUpdate() >= 1;
        } catch (Exception e) {
            log.warn("upsertOrgPolicy failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    public boolean configureOutbound(UUID id, UUID targetChatId, UUID actorUserId, String tokenHash) {
        var sql = """
            UPDATE plugin_instances
            SET outbound_target_chat_id = ?, outbound_actor_user_id = ?, outbound_token_hash = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(ps);
            ps.setObject(1, targetChatId);
            ps.setObject(2, actorUserId);
            ps.setString(3, tokenHash);
            ps.setObject(4, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            log.warn("configureOutbound failed: {}", e.getMessage());
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private static PluginRepository.OrgPolicyRow mapPolicy(java.sql.ResultSet rs) throws Exception {
        List<String> allowed = new ArrayList<>();
        var allowedJson = rs.getString(2);
        if (allowedJson != null && !allowedJson.isBlank()) {
            var node = MAPPER.readTree(allowedJson);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    allowed.add(item.asText());
                }
            }
        }
        return new PluginRepository.OrgPolicyRow(
            (UUID) rs.getObject(1),
            List.copyOf(allowed),
            rs.getString(3),
            rs.getBoolean(4),
            rs.getTimestamp(5).toInstant()
        );
    }

    private static PluginRepository.InstanceRow mapInstance(java.sql.ResultSet rs) throws Exception {
        var configText = rs.getString(9);
        JsonNode config = configText != null && !configText.isBlank()
            ? MAPPER.readTree(configText)
            : MAPPER.createObjectNode();
        return new PluginRepository.InstanceRow(
            (UUID) rs.getObject(1),
            (UUID) rs.getObject(2),
            rs.getString(3),
            rs.getString(4),
            rs.getString(5),
            rs.getBoolean(6),
            rs.getString(7),
            rs.getString(8),
            config,
            rs.getTimestamp(10).toInstant(),
            rs.getTimestamp(11).toInstant(),
            (UUID) rs.getObject(12),
            (UUID) rs.getObject(13),
            rs.getString(14)
        );
    }
}
