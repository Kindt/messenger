package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcPluginJdbcRepository;
import com.fasterxml.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for plugin JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcPluginJdbcRepository}.
 */
public class PluginRepository {
    private final JdbcPluginJdbcRepository jdbc;

    public PluginRepository(DataSource dataSource) {
        this.jdbc = new JdbcPluginJdbcRepository(dataSource);
    }

    public JdbcPluginJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public record PresetRow(
        String id,
        String pluginClass,
        String runtimeKind,
        int configSchemaVersion,
        String capabilitiesJson
    ) {}

    public record OrgPolicyRow(
        UUID orgId,
        List<String> allowedPresetIds,
        String llmMode,
        boolean ocrOnPremOnly,
        Instant updatedAt
    ) {}

    public record InstanceRow(
        UUID id,
        UUID orgId,
        String presetId,
        String botName,
        String displayName,
        boolean enabled,
        String pluginClass,
        String runtimeEndpoint,
        JsonNode configJson,
        Instant createdAt,
        Instant updatedAt,
        UUID outboundTargetChatId,
        UUID outboundActorUserId,
        String outboundTokenHash
    ) {}

    public List<PresetRow> listPresets() {
        return jdbc.listPresets();
    }

    public List<InstanceRow> listInstances(UUID orgId) {
        return listInstances(orgId, 500, 0).rows();
    }

    public record InstancePage(List<InstanceRow> rows, int total) {}

    public InstancePage listInstances(UUID orgId, int limit, int offset) {
        return jdbc.listInstances(orgId, limit, offset);
    }

    public int countInstances(UUID orgId) {
        return jdbc.countInstances(orgId);
    }

    public Optional<InstanceRow> findInstanceByOrgAndBotName(UUID orgId, String botName) {
        return jdbc.findInstanceByOrgAndBotName(orgId, botName);
    }

    public boolean setInstanceEnabled(UUID id, boolean enabled) {
        return jdbc.setInstanceEnabled(id, enabled);
    }

    public Optional<InstanceRow> findInstance(UUID id) {
        return jdbc.findInstance(id);
    }

    public boolean insertInstance(InstanceRow row) {
        return jdbc.insertInstance(row);
    }

    public Optional<OrgPolicyRow> findOrgPolicy(UUID orgId) {
        return jdbc.findOrgPolicy(orgId);
    }

    public boolean upsertOrgPolicy(OrgPolicyRow row) {
        return jdbc.upsertOrgPolicy(row);
    }

    public boolean configureOutbound(UUID id, UUID targetChatId, UUID actorUserId, String tokenHash) {
        return jdbc.configureOutbound(id, targetChatId, actorUserId, tokenHash);
    }
}
