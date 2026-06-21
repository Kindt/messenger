package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Migrates legacy E2EE chats to MLS group state ({@code migrateToMls}, batch job). */
public class MlsMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MlsMigrationService.class);

    private final AdminMetricsQueryPort adminMetricsQueryPort;
    private final MlsGroupManager groupManager;
    private final ChatPersistencePort chatPersistencePort;

    public MlsMigrationService(
        AdminMetricsQueryPort adminMetricsQueryPort,
        MlsGroupManager groupManager,
        ChatPersistencePort chatPersistencePort
    ) {
        this.adminMetricsQueryPort = adminMetricsQueryPort;
        this.groupManager = groupManager;
        this.chatPersistencePort = chatPersistencePort;
    }

    public long pendingMigrationCount() {
        if (adminMetricsQueryPort == null) {
            return 0L;
        }
        return adminMetricsQueryPort.countPendingMlsMigrations();
    }

    public Optional<UUID> migrateToMls(UUID chatId) {
        if (chatId == null || groupManager == null) {
            return Optional.empty();
        }
        var existing = groupManager.findGroupByChatId(chatId);
        if (existing.isPresent()) {
            return Optional.of(existing.get().groupId());
        }
        var members = chatPersistencePort.listMembers(chatId).stream()
            .map(m -> UUID.fromString(m.userId()))
            .toList();
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

    /** Ensures MLS group exists using OpenMLS wire profile ({@link OpenMlsWireLayout#WIRE_PROFILE}). */
    public Optional<UUID> migrateToOpenMlsGroup(UUID chatId) {
        var groupId = migrateToMls(chatId);
        if (groupId.isPresent()) {
            log.info("OpenMLS wire profile {} applied for chat {}", OpenMlsWireLayout.WIRE_PROFILE, chatId);
        }
        return groupId;
    }

    /**
     * Batch migration for OpenMLS wire profile (idempotent when group already exists).
     */
    public BatchMigrationResult batchMigrateToOpenMls(int limit) {
        var capped = Math.max(1, Math.min(limit, 500));
        var pending = listPendingChatIds(capped);
        var migrated = new ArrayList<UUID>();
        var failed = new ArrayList<UUID>();
        for (var chatId : pending) {
            var result = migrateToOpenMlsGroup(chatId);
            if (result.isPresent()) {
                migrated.add(chatId);
            } else {
                failed.add(chatId);
            }
        }
        return new BatchMigrationResult(migrated.size(), failed.size(), pendingMigrationCount(), migrated, failed);
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
        if (adminMetricsQueryPort == null || limit <= 0) {
            return List.of();
        }
        return adminMetricsQueryPort.listPendingMlsMigrationChatIds(limit);
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
