package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Migrates legacy E2EE chats to MLS group state ({@code migrateToMls}, batch job). */
public class MlsMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MlsMigrationService.class);

    private final DataSource dataSource;
    private final MlsGroupManager groupManager;
    private final ChatRepository chatRepository;

    public MlsMigrationService(DataSource dataSource, MlsGroupManager groupManager, ChatRepository chatRepository) {
        this.dataSource = dataSource;
        this.groupManager = groupManager;
        this.chatRepository = chatRepository;
    }

    public long pendingMigrationCount() {
        if (dataSource == null) {
            return 0L;
        }
        var sql = """
            SELECT COUNT(DISTINCT s.chat_id) AS c
            FROM e2ee_sessions s
            LEFT JOIN mls_group_state g ON g.chat_id = s.chat_id
            WHERE g.chat_id IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("c");
            }
        } catch (Exception e) {
            log.error("pendingMigrationCount failed", e);
        }
        return 0L;
    }

    public Optional<UUID> migrateToMls(UUID chatId) {
        if (chatId == null || groupManager == null) {
            return Optional.empty();
        }
        var existing = groupManager.findGroupByChatId(chatId);
        if (existing.isPresent()) {
            return Optional.of(existing.get().groupId());
        }
        var members = chatRepository.listMembers(chatId).stream()
            .map(m -> UUID.fromString(m.userId()))
            .collect(Collectors.toList());
        if (members.isEmpty()) {
            log.warn("migrateToMls: no members for chat {}", chatId);
            return Optional.empty();
        }
        var groupId = groupManager.createGroup(chatId, members);
        if (groupId == null) {
            return Optional.empty();
        }
        log.info("Migrated chat {} to MLS group {}", chatId, groupId);
        return Optional.of(groupId);
    }

    /**
     * Batch migration stub: migrates up to {@code limit} chats that have legacy E2EE sessions but no MLS group.
     */
    public BatchMigrationResult batchMigrateToMls(int limit) {
        var capped = Math.max(1, Math.min(limit, 500));
        var pending = listPendingChatIds(capped);
        var migrated = new ArrayList<UUID>();
        var failed = new ArrayList<UUID>();
        for (var chatId : pending) {
            var result = migrateToMls(chatId);
            if (result.isPresent()) {
                migrated.add(chatId);
            } else {
                failed.add(chatId);
            }
        }
        return new BatchMigrationResult(migrated.size(), failed.size(), pendingMigrationCount(), migrated, failed);
    }

    List<UUID> listPendingChatIds(int limit) {
        if (dataSource == null || limit <= 0) {
            return List.of();
        }
        var sql = """
            SELECT DISTINCT s.chat_id
            FROM e2ee_sessions s
            LEFT JOIN mls_group_state g ON g.chat_id = s.chat_id
            WHERE g.chat_id IS NULL
            ORDER BY s.updated_at ASC
            LIMIT ?
            """;
        var out = new ArrayList<UUID>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getObject("chat_id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("listPendingChatIds failed", e);
        }
        return out;
    }

    public record BatchMigrationResult(
        int migratedCount,
        int failedCount,
        long remainingPending,
        List<UUID> migratedChatIds,
        List<UUID> failedChatIds
    ) {
    }
}
