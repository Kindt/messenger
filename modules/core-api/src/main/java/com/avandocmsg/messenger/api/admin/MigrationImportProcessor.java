package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.migration.TelegramExportV1Parser;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Batch processor for migration import jobs (spec 022 T02273). */
public class MigrationImportProcessor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TG_CLIENT_PREFIX = "tg-import:";

    private final MigrationImportJobRepository jobRepository;
    private final ChatRepository chatRepository;
    private final MessageRepositoryPort messageRepositoryPort;
    private final UuidGenerator uuidGenerator;

    public MigrationImportProcessor(MigrationImportJobRepository jobRepository) {
        this(jobRepository, null, null, UuidGenerator.standard());
    }

    public MigrationImportProcessor(
        MigrationImportJobRepository jobRepository,
        ChatRepository chatRepository,
        MessageRepositoryPort messageRepositoryPort,
        UuidGenerator uuidGenerator
    ) {
        this.jobRepository = jobRepository;
        this.chatRepository = chatRepository;
        this.messageRepositoryPort = messageRepositoryPort;
        this.uuidGenerator = uuidGenerator != null ? uuidGenerator : UuidGenerator.standard();
    }

    public Optional<MigrationImportJobRepository.JobRow> process(UUID jobId) {
        var job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        if (!"pending".equals(job.status()) && !"failed".equals(job.status())) {
            return Optional.of(job);
        }
        if (chatRepository == null || messageRepositoryPort == null) {
            return fail(jobId, "processor not configured with repositories");
        }
        jobRepository.updateStatus(jobId, "running", "{\"phase\":\"running\"}");
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
            if (!chatRepository.chatExists(chatId)) {
                var title = config.has("target_chat_title") && config.get("target_chat_title").isTextual()
                    ? config.get("target_chat_title").asText()
                    : parsed.chatTitle();
                var created = chatRepository.createGroup(chatId, title, actorId);
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
            jobRepository.updateStatus(jobId, "completed", MAPPER.writeValueAsString(result));
            return jobRepository.findById(jobId);
        } catch (Exception e) {
            return fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private Optional<MigrationImportJobRepository.JobRow> fail(UUID jobId, String error) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("error", error);
            jobRepository.updateStatus(jobId, "failed", MAPPER.writeValueAsString(node));
        } catch (Exception ignored) {
            jobRepository.updateStatus(jobId, "failed", "{\"error\":\"import failed\"}");
        }
        return jobRepository.findById(jobId);
    }

    private static UUID deterministicChatId(UUID jobId) {
        return UUID.nameUUIDFromBytes(
            ("migration-import:" + jobId).getBytes(StandardCharsets.UTF_8));
    }
}
