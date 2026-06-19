package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.migration.TelegramExportV1Parser;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Batch processor for migration import jobs (spec 022 T02273). */
public class MigrationImportProcessor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TG_CLIENT_PREFIX = "tg-import:";

    private final MigrationImportJobPort migrationImportJobPort;
    private final ChatPersistencePort chatPersistencePort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final UuidGenerator uuidGenerator;

    public MigrationImportProcessor(MigrationImportJobPort migrationImportJobPort) {
        this(migrationImportJobPort, null, null, UuidGenerator.standard());
    }

    public MigrationImportProcessor(
        MigrationImportJobPort migrationImportJobPort,
        ChatPersistencePort chatPersistencePort,
        MessageRepositoryPort messageRepositoryPort,
        UuidGenerator uuidGenerator
    ) {
        this.migrationImportJobPort = migrationImportJobPort;
        this.chatPersistencePort = chatPersistencePort;
        this.messageRepositoryPort = messageRepositoryPort;
        this.uuidGenerator = uuidGenerator != null ? uuidGenerator : UuidGenerator.standard();
    }

    public Optional<MigrationImportJobPort.JobRow> process(UUID jobId) {
        var job = migrationImportJobPort.findById(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        if (!"pending".equals(job.status()) && !"failed".equals(job.status())) {
            return Optional.of(job);
        }
        if (chatPersistencePort == null || messageRepositoryPort == null) {
            return fail(jobId, "processor not configured with repositories");
        }
        migrationImportJobPort.updateStatus(jobId, "running", "{\"phase\":\"running\"}");
        try {
            if (!"telegram_export_v1".equals(job.source())) {
                return fail(jobId, "unsupported source: " + job.source());
            }
            var actorId = job.createdBy();
            if (actorId == null) {
                return fail(jobId, "created_by missing on job");
            }
            var config = MAPPER.readTree(job.configJson() != null ? job.configJson() : "{}");
            var parsed = TelegramExportV1Parser.parse(config);
            var chatId = deterministicChatId(jobId);
            if (!chatPersistencePort.chatExists(chatId)) {
                var title = config.has("target_chat_title") && config.get("target_chat_title").isTextual()
                    ? config.get("target_chat_title").asText()
                    : parsed.chatTitle();
                var created = chatPersistencePort.createGroup(chatId, title, actorId);
                if (created == null) {
                    return fail(jobId, "failed to create import chat");
                }
            }
            var chatIdDomain = ChatId.of(chatId);
            var actorIdDomain = UserId.of(actorId);
            int imported = 0;
            int skipped = 0;
            for (var msg : parsed.messages()) {
                var clientMsgId = TG_CLIENT_PREFIX + msg.exportId();
                if (messageRepositoryPort.existsClientMsgId(chatIdDomain, actorIdDomain, clientMsgId)) {
                    skipped++;
                    continue;
                }
                var inserted = messageRepositoryPort.insert(new MessageInsert(
                    MessageId.of(uuidGenerator.randomUuid()),
                    chatIdDomain,
                    actorIdDomain,
                    "text",
                    msg.text(),
                    null,
                    null,
                    clientMsgId,
                    null,
                    null,
                    null));
                if (inserted.isPresent()) {
                    imported++;
                } else {
                    skipped++;
                }
            }
            var result = MAPPER.createObjectNode();
            result.put("imported_messages", imported);
            result.put("skipped_messages", skipped);
            result.put("imported_users", 0);
            result.put("chat_id", chatId.toString());
            migrationImportJobPort.updateStatus(jobId, "completed", MAPPER.writeValueAsString(result));
            return migrationImportJobPort.findById(jobId);
        } catch (Exception e) {
            return fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private Optional<MigrationImportJobPort.JobRow> fail(UUID jobId, String error) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("error", error);
            migrationImportJobPort.updateStatus(jobId, "failed", MAPPER.writeValueAsString(node));
        } catch (Exception ignored) {
            migrationImportJobPort.updateStatus(jobId, "failed", "{\"error\":\"import failed\"}");
        }
        return migrationImportJobPort.findById(jobId);
    }

    private static UUID deterministicChatId(UUID jobId) {
        return UUID.nameUUIDFromBytes(
            ("migration-import:" + jobId).getBytes(StandardCharsets.UTF_8));
    }
}
