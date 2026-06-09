package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Migrates legacy E2EE chats to MLS group state ({@code migrateToMls}). */
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
}
