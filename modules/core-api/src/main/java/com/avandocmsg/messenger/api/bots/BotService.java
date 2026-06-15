package com.avandocmsg.messenger.api.bots;

import com.avandocmsg.messenger.api.bots.dto.BotResponse;
import com.avandocmsg.messenger.api.bots.dto.CreateBotRequest;
import com.avandocmsg.messenger.api.bots.dto.CreateBotResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class BotService {
    private static final Pattern BOT_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    private final BotRepository botRepository;
    private final ChatRepository chatRepository;
    private final MessageApplicationService messageApplicationService;
    private final AuditRepository auditRepository;
    private final UuidGenerator uuidGenerator;

    public BotService(BotRepository botRepository, ChatRepository chatRepository,
                      MessageApplicationService messageApplicationService,
                      AuditRepository auditRepository, UuidGenerator uuidGenerator) {
        this.botRepository = botRepository;
        this.chatRepository = chatRepository;
        this.messageApplicationService = messageApplicationService;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
    }

    public enum CreateOutcome { SUCCESS, INVALID_NAME, INVALID_WEBHOOK, INVALID_LISTEN_MODE, NAME_TAKEN, PERSISTENCE_FAILED }

    public record CreateResult(CreateOutcome outcome, CreateBotResponse response) {}

    public CreateResult create(UUID ownerId, CreateBotRequest request) {
        if (request == null || request.botName() == null || !BOT_NAME.matcher(request.botName()).matches()) {
            return new CreateResult(CreateOutcome.INVALID_NAME, null);
        }
        var listenMode = normalizeListenMode(request.listenMode());
        if (listenMode == null) {
            return new CreateResult(CreateOutcome.INVALID_LISTEN_MODE, null);
        }
        var webhook = trimToNull(request.defaultWebhookUrl());
        if (webhook != null && !isHttpsUrl(webhook)) {
            return new CreateResult(CreateOutcome.INVALID_WEBHOOK, null);
        }
        if (botRepository.findByBotName(request.botName()).isPresent()) {
            return new CreateResult(CreateOutcome.NAME_TAKEN, null);
        }
        var botId = uuidGenerator.randomUuid();
        var token = BotTokenHasher.generateToken();
        var tokenHash = BotTokenHasher.hashToken(token);
        var displayName = request.displayName() != null && !request.displayName().isBlank()
            ? request.displayName().trim()
            : request.botName();
        if (!botRepository.createBot(botId, ownerId, null, request.botName(), displayName, tokenHash, listenMode, webhook)) {
            return new CreateResult(CreateOutcome.PERSISTENCE_FAILED, null);
        }
        auditRepository.record(ownerId, "bot.create", "bot", botId.toString(), null);
        return new CreateResult(CreateOutcome.SUCCESS, new CreateBotResponse(
            botId.toString(),
            request.botName(),
            displayName,
            listenMode,
            webhook,
            token));
    }

    public List<BotResponse> listOwned(UUID ownerId) {
        return botRepository.listByOwner(ownerId).stream().map(this::toResponse).toList();
    }

    public Optional<BotResponse> getOwned(UUID ownerId, UUID botId) {
        return botRepository.findById(botId)
            .filter(b -> b.ownerId().equals(ownerId))
            .map(this::toResponse);
    }

    public boolean updateWebhook(UUID ownerId, UUID botId, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank() || !isHttpsUrl(webhookUrl.trim())) {
            return false;
        }
        var ok = botRepository.updateDefaultWebhook(botId, ownerId, webhookUrl.trim());
        if (ok) {
            auditRepository.record(ownerId, "bot.webhook.update", "bot", botId.toString(), null);
        }
        return ok;
    }

    public enum SubscribeOutcome { SUCCESS, NOT_FOUND, NOT_MEMBER, INVALID_WEBHOOK, NO_WEBHOOK, PERSISTENCE_FAILED }

    public SubscribeOutcome subscribe(UUID ownerId, UUID botId, UUID chatId, String webhookOverride) {
        var bot = botRepository.findById(botId).filter(b -> b.ownerId().equals(ownerId));
        if (bot.isEmpty()) {
            return SubscribeOutcome.NOT_FOUND;
        }
        if (chatRepository.getMemberRole(chatId, ownerId) == null) {
            return SubscribeOutcome.NOT_MEMBER;
        }
        var url = trimToNull(webhookOverride);
        if (url == null) {
            url = bot.get().defaultWebhookUrl();
        }
        if (url == null || url.isBlank()) {
            return SubscribeOutcome.NO_WEBHOOK;
        }
        if (!isHttpsUrl(url)) {
            return SubscribeOutcome.INVALID_WEBHOOK;
        }
        chatRepository.addMember(chatId, botId, "member");
        if (!botRepository.upsertSubscription(botId, chatId, url.trim())) {
            return SubscribeOutcome.PERSISTENCE_FAILED;
        }
        auditRepository.record(ownerId, "bot.subscribe", "chat", chatId.toString(), botId.toString());
        return SubscribeOutcome.SUCCESS;
    }

    public boolean unsubscribe(UUID ownerId, UUID botId, UUID chatId) {
        var bot = botRepository.findById(botId).filter(b -> b.ownerId().equals(ownerId));
        if (bot.isEmpty()) {
            return false;
        }
        var ok = botRepository.deleteSubscription(botId, chatId);
        if (ok) {
            auditRepository.record(ownerId, "bot.unsubscribe", "chat", chatId.toString(), botId.toString());
        }
        return ok;
    }

    public Optional<BotRepository.BotRow> authenticateToken(String token) {
        if (token == null || token.isBlank() || !token.startsWith("kbt_")) {
            return Optional.empty();
        }
        return botRepository.findByTokenHash(BotTokenHasher.hashToken(token.trim()));
    }

    public MessageResponse sendMessage(UUID botUserId, UUID chatId, SendMessageRequest request) {
        return messageApplicationService.sendMessage(chatId, botUserId, request, null);
    }

    private BotResponse toResponse(BotRepository.BotRow row) {
        return new BotResponse(
            row.id().toString(),
            row.botName(),
            row.displayName(),
            row.listenMode(),
            row.defaultWebhookUrl(),
            row.createdAt().toEpochMilli());
    }

    static String normalizeListenMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MENTIONS_ONLY";
        }
        var mode = raw.trim().toUpperCase();
        if ("MENTIONS_ONLY".equals(mode) || "READ_ALL".equals(mode)) {
            return mode;
        }
        return null;
    }

    static boolean isHttpsUrl(String url) {
        try {
            var uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
