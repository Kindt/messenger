package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.export.ExportGdprDisclosures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Loads effective chat retention policy for export JSON (platform → org → chat). */
final class ExportRetentionPolicyLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExportRetentionPolicyLoader() {
    }

    static ObjectNode loadEffectivePolicy(
        DataSource dataSource,
        UUID chatId,
        ExportPlatformDefaults platform,
        int jdbcQueryTimeoutSeconds
    ) throws SQLException {
        var baseOrgId = findBaseOrgId(dataSource, chatId, jdbcQueryTimeoutSeconds);
        var chatPolicy = loadChatPolicy(dataSource, chatId, jdbcQueryTimeoutSeconds);
        Optional<OrgPolicyRow> orgPolicy = Optional.empty();
        if (baseOrgId.isPresent()) {
            orgPolicy = loadOrgPolicy(dataSource, baseOrgId.get(), jdbcQueryTimeoutSeconds);
        }
        return toJsonNode(baseOrgId, chatPolicy, orgPolicy, platform);
    }

    static ObjectNode toJsonNode(
        Optional<UUID> baseOrgId,
        Optional<ChatPolicyRow> chatPolicy,
        Optional<OrgPolicyRow> orgPolicy,
        ExportPlatformDefaults platform
    ) {
        var orgLayer = orgPolicy.map(OrgPolicyRow::toLayer).orElseGet(() -> Layer.fromPlatform(platform));
        var effective = chatPolicy.map(ch -> Layer.merge(orgLayer, ch)).orElse(orgLayer);

        var node = MAPPER.createObjectNode();
        baseOrgId.ifPresentOrElse(id -> node.put("baseOrgId", id.toString()), () -> node.putNull("baseOrgId"));
        putNullableInt(node, "hotMessageBodyMaxAgeDays", effective.hotBodyMaxAgeDays());
        putNullableInt(node, "hotMetadataMinAgeDays", effective.hotMetadataMinAgeDays());
        node.put("archiveMetadataEnabled", effective.archiveMetadataEnabled());
        node.put("deepArchiveEnabled", effective.deepArchiveEnabled());
        node.put("legalHold", effective.legalHold());
        node.put(
            "exportRecommendedBeforeHotBodyPurge",
            effective.exportRecommendedBeforeHotBodyPurge()
        );
        return node;
    }

    static ObjectNode buildExportCompleteness(
        boolean messageTtlFilterApplied,
        boolean includeVersions,
        boolean includeReactions,
        boolean includePins,
        boolean includeReferencedFiles,
        boolean deepArchiveSnapshotsRequested,
        int deepArchiveMessagesScanned,
        int deepArchiveSnapshotsFound,
        boolean deepArchiveFileIdsReferenced,
        boolean retentionSnapshotsRequested,
        int retentionMessagesScanned,
        int retentionSnapshotsFound,
        boolean solrIndexRequested,
        int solrNumFound,
        int solrExported,
        int e2eeMessageCount,
        int nonE2eeMessageCount,
        boolean fileBodiesRequested,
        boolean e2eeFileCandidatesIncluded,
        int e2eeFileCandidateCount
    ) {
        var node = MAPPER.createObjectNode();
        node.put("formatVersion", 1);
        node.put("hotDbMessageRowsIncluded", true);
        node.put("messageTtlFilterApplied", messageTtlFilterApplied);
        node.put("e2eeMessagePlaintextOmitted", true);
        node.put("e2eeMessageCount", e2eeMessageCount);
        node.put("nonE2eeMessageCount", nonE2eeMessageCount);
        node.put("e2eeFileIdsFromContentSkipped", true);
        node.put("e2eeFileCandidatesIncluded", e2eeFileCandidatesIncluded);
        node.put("e2eeFileCandidateCount", e2eeFileCandidateCount);
        node.put("messageVersionsIncluded", includeVersions);
        node.put("reactionsIncluded", includeReactions);
        node.put("pinnedMessagesIncluded", includePins);
        node.put("referencedFileMetadataIncluded", includeReferencedFiles);
        node.put("deepArchiveSnapshotsRequested", deepArchiveSnapshotsRequested);
        node.put("deepArchiveMessagesScanned", deepArchiveMessagesScanned);
        node.put("deepArchiveSnapshotsFound", deepArchiveSnapshotsFound);
        node.put("deepArchiveMessageBodiesIncluded", deepArchiveSnapshotsFound > 0);
        node.put("deepArchiveReferencedFileIds", deepArchiveFileIdsReferenced);
        node.put("retentionSnapshotsRequested", retentionSnapshotsRequested);
        node.put("retentionMessagesScanned", retentionMessagesScanned);
        node.put("retentionSnapshotsFound", retentionSnapshotsFound);
        node.put("retentionMinioSnapshotsIncluded", retentionSnapshotsFound > 0);
        node.put("solrIndexRequested", solrIndexRequested);
        node.put("solrIndexNumFound", solrNumFound);
        node.put("solrIndexExported", solrExported);
        node.put("solrIndexIncluded", solrExported > 0);
        node.put(
            "operatorNote",
            "For compliance before aggressive retention, run chat export while messages are still in hot DB; "
                + "re-export after policy changes if needed."
        );
        node.set(
            "gdprDisclosures",
            buildGdprDisclosures(
                messageTtlFilterApplied,
                includeVersions,
                includeReactions,
                includePins,
                includeReferencedFiles,
                deepArchiveSnapshotsRequested,
                deepArchiveSnapshotsFound,
                deepArchiveFileIdsReferenced,
                retentionSnapshotsRequested,
                retentionSnapshotsFound,
                solrIndexRequested,
                solrExported,
                e2eeMessageCount,
                nonE2eeMessageCount,
                fileBodiesRequested,
                e2eeFileCandidatesIncluded,
                e2eeFileCandidateCount
            )
        );
        return node;
    }

    static ArrayNode buildGdprDisclosures(
        boolean messageTtlFilterApplied,
        boolean includeVersions,
        boolean includeReactions,
        boolean includePins,
        boolean includeReferencedFiles,
        boolean deepArchiveSnapshotsRequested,
        int deepArchiveSnapshotsFound,
        boolean deepArchiveFileIdsReferenced,
        boolean retentionSnapshotsRequested,
        int retentionSnapshotsFound,
        boolean solrIndexRequested,
        int solrExported,
        int e2eeMessageCount,
        int nonE2eeMessageCount,
        boolean fileBodiesRequested,
        boolean e2eeFileCandidatesIncluded,
        int e2eeFileCandidateCount
    ) {
        return ExportGdprDisclosures.build(
            messageTtlFilterApplied,
            includeVersions,
            includeReactions,
            includePins,
            includeReferencedFiles,
            deepArchiveSnapshotsRequested,
            deepArchiveSnapshotsFound,
            deepArchiveFileIdsReferenced,
            retentionSnapshotsRequested,
            retentionSnapshotsFound,
            solrIndexRequested,
            solrExported,
            e2eeMessageCount,
            nonE2eeMessageCount,
            fileBodiesRequested,
            e2eeFileCandidatesIncluded,
            e2eeFileCandidateCount);
    }

    private static Optional<UUID> findBaseOrgId(DataSource dataSource, UUID chatId, int queryTimeout) throws SQLException {
        var ownerSql = """
            SELECT u.org_id
            FROM chats c
            JOIN users u ON u.id = c.owner_id
            WHERE c.id = ? AND u.org_id IS NOT NULL
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(ownerSql)) {
            applyTimeout(ps, queryTimeout);
            ps.setObject(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("org_id", UUID.class));
                }
            }
        }
        var memberSql = """
            SELECT u.org_id
            FROM chat_members cm
            JOIN users u ON u.id = cm.user_id
            WHERE cm.chat_id = ? AND u.org_id IS NOT NULL
            ORDER BY CASE cm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, cm.user_id
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(memberSql)) {
            applyTimeout(ps, queryTimeout);
            ps.setObject(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("org_id", UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ChatPolicyRow> loadChatPolicy(DataSource dataSource, UUID chatId, int queryTimeout)
        throws SQLException {
        var sql = """
            SELECT hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold
            FROM chat_retention_policy WHERE chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            applyTimeout(ps, queryTimeout);
            ps.setObject(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(ChatPolicyRow.from(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<OrgPolicyRow> loadOrgPolicy(DataSource dataSource, UUID orgId, int queryTimeout)
        throws SQLException {
        var sql = """
            SELECT hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold
            FROM org_retention_policy WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            applyTimeout(ps, queryTimeout);
            ps.setObject(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(OrgPolicyRow.from(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static void applyTimeout(PreparedStatement ps, int seconds) throws SQLException {
        if (seconds > 0) {
            ps.setQueryTimeout(seconds);
        }
    }

    private static void putNullableInt(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    record ChatPolicyRow(
        Integer hotBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold
    ) {
        static ChatPolicyRow from(ResultSet rs) throws SQLException {
            return new ChatPolicyRow(
                (Integer) rs.getObject("hot_message_body_max_age_days"),
                (Integer) rs.getObject("hot_metadata_min_age_days"),
                rs.getBoolean("archive_metadata_enabled"),
                rs.getBoolean("deep_archive_enabled"),
                rs.getBoolean("legal_hold")
            );
        }
    }

    record OrgPolicyRow(
        Integer hotBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold
    ) {
        static OrgPolicyRow from(ResultSet rs) throws SQLException {
            return new OrgPolicyRow(
                (Integer) rs.getObject("hot_message_body_max_age_days"),
                (Integer) rs.getObject("hot_metadata_min_age_days"),
                rs.getBoolean("archive_metadata_enabled"),
                rs.getBoolean("deep_archive_enabled"),
                rs.getBoolean("legal_hold")
            );
        }

        Layer toLayer() {
            return new Layer(hotBodyMaxAgeDays, hotMetadataMinAgeDays, archiveMetadataEnabled, deepArchiveEnabled, legalHold);
        }
    }

    record Layer(
        Integer hotBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold
    ) {
        static Layer fromPlatform(ExportPlatformDefaults platform) {
            return new Layer(
                platform.hotBodyMaxAgeDays(),
                platform.hotMetadataMinAgeDays(),
                platform.archiveMetadataEnabled(),
                platform.deepArchiveEnabled(),
                platform.legalHold()
            );
        }

        static Layer merge(Layer org, ChatPolicyRow chat) {
            return new Layer(
                chat.hotBodyMaxAgeDays() != null ? chat.hotBodyMaxAgeDays() : org.hotBodyMaxAgeDays(),
                chat.hotMetadataMinAgeDays() != null ? chat.hotMetadataMinAgeDays() : org.hotMetadataMinAgeDays(),
                chat.archiveMetadataEnabled(),
                chat.deepArchiveEnabled(),
                chat.legalHold()
            );
        }

        boolean exportRecommendedBeforeHotBodyPurge() {
            return !legalHold && deepArchiveEnabled && hotBodyMaxAgeDays != null && hotBodyMaxAgeDays > 0;
        }
    }
}
