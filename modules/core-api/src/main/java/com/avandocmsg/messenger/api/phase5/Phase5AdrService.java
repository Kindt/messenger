package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.plugins.PluginPlatformService;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ConferencePort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Phase5AdrService {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String AI_PRESET = "ai-chat-gateway";
    private static final String STT_PRESET = "stt-mock";

    private final Phase5AdrRepository repository;
    private final ChatPersistencePort chatPersistencePort;
    private final ConferencePort conferencePort;
    private final UserLookupPort userLookupPort;
    private final PluginRepository pluginRepository;
    private final PluginPlatformService pluginPlatformService;
    private final AppConfig appConfig;

    public Phase5AdrService(
        Phase5AdrRepository repository,
        ChatPersistencePort chatPersistencePort,
        ConferencePort conferencePort,
        UserLookupPort userLookupPort,
        PluginRepository pluginRepository,
        PluginPlatformService pluginPlatformService,
        AppConfig appConfig
    ) {
        this.repository = repository;
        this.chatPersistencePort = chatPersistencePort;
        this.conferencePort = conferencePort;
        this.userLookupPort = userLookupPort;
        this.pluginRepository = pluginRepository;
        this.pluginPlatformService = pluginPlatformService;
        this.appConfig = appConfig;
    }

    public boolean isChatMember(UUID chatId, UUID userId) {
        return chatPersistencePort.getMemberRole(chatId, userId) != null;
    }

    public Optional<UUID> orgIdForUser(UUID userId) {
        var fromProfile = userLookupPort.findById(userId)
            .map(p -> p.orgId())
            .filter(s -> s != null && !s.isBlank())
            .map(UUID::fromString);
        if (fromProfile.isPresent()) {
            return fromProfile;
        }
        return appConfig.defaultOrgId();
    }

    public boolean conferenceInChat(UUID conferenceId, UUID chatId) {
        return conferencePort.findById(conferenceId)
            .map(c -> chatId.toString().equals(c.chatId()))
            .orElse(false);
    }

    public List<Phase5AdrRepository.StickerPackRow> listStickerPacks(UUID userId) {
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null) {
            return List.of();
        }
        return repository.listStickerPacks(orgId);
    }

    public Optional<Phase5AdrRepository.StickerPackRow> createStickerPack(UUID userId, String name) {
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        var id = repository.createStickerPack(orgId, name.trim());
        return repository.listStickerPacks(orgId).stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<Phase5AdrRepository.GifRow> searchGifs(UUID userId, String query) {
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null) {
            return List.of();
        }
        repository.seedDefaultGifs(orgId);
        return repository.searchGifs(orgId, query);
    }

    public Optional<UUID> startRecording(UUID chatId, UUID conferenceId, UUID userId) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return Optional.empty();
        }
        return Optional.of(repository.startRecording(conferenceId, chatId, userId));
    }

    public List<Phase5AdrRepository.RecordingRow> listRecordings(UUID chatId, UUID conferenceId, UUID userId) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return List.of();
        }
        return repository.listRecordings(conferenceId);
    }

    public Optional<Boolean> completeRecording(UUID chatId, UUID conferenceId, UUID userId, UUID recordingId) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId) || recordingId == null) {
            return Optional.empty();
        }
        return repository.completeRecording(recordingId, conferenceId)
            ? Optional.of(Boolean.TRUE)
            : Optional.empty();
    }

    public Optional<Phase5AdrRepository.GuestLinkRow> createGuestLink(
        UUID chatId, UUID conferenceId, UUID userId, boolean waitingRoom
    ) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return Optional.empty();
        }
        return Optional.of(repository.createGuestLink(conferenceId, chatId, waitingRoom, null));
    }

    public Optional<UUID> createBreakout(UUID chatId, UUID conferenceId, UUID userId, String name) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return Optional.empty();
        }
        var room = "breakout-" + UUID.randomUUID().toString().substring(0, 8);
        return Optional.of(repository.createBreakout(conferenceId, chatId, name, room));
    }

    public List<Phase5AdrRepository.BreakoutRow> listBreakouts(UUID chatId, UUID conferenceId, UUID userId) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return List.of();
        }
        return repository.listBreakouts(conferenceId);
    }

    public Optional<Phase5AdrRepository.WhiteboardRow> getWhiteboard(UUID chatId, UUID userId) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        return repository.getWhiteboard(chatId);
    }

    public Optional<Phase5AdrRepository.WhiteboardRow> saveWhiteboard(
        UUID chatId, UUID userId, String title, String snapshotJson
    ) {
        if (!isChatMember(chatId, userId)) {
            return Optional.empty();
        }
        return Optional.of(repository.upsertWhiteboard(chatId, userId, title, snapshotJson));
    }

    public Optional<UUID> createKanbanTask(UUID chatId, UUID userId, String columnKey, String title) {
        if (!isChatMember(chatId, userId) || title == null || title.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(repository.createKanbanTask(chatId, userId, columnKey, title.trim(), null));
    }

    public List<Phase5AdrRepository.KanbanTaskRow> listKanbanTasks(UUID chatId, UUID userId) {
        if (!isChatMember(chatId, userId)) {
            return List.of();
        }
        return repository.listKanbanTasks(chatId);
    }

    public Optional<Phase5AdrRepository.KanbanTaskRow> updateKanbanTask(
        UUID chatId,
        UUID userId,
        UUID taskId,
        String columnKey,
        Integer sortOrder,
        String title
    ) {
        if (!isChatMember(chatId, userId) || taskId == null) {
            return Optional.empty();
        }
        return repository.updateKanbanTask(taskId, chatId, columnKey, sortOrder, title);
    }

    public Optional<Boolean> deleteKanbanTask(UUID chatId, UUID userId, UUID taskId) {
        if (!isChatMember(chatId, userId) || taskId == null) {
            return Optional.empty();
        }
        return repository.deleteKanbanTask(taskId, chatId) ? Optional.of(Boolean.TRUE) : Optional.empty();
    }

    public Optional<GuestRedeemResult> redeemGuestLink(String token) {
        return repository.findGuestLinkByToken(token)
            .map(row -> {
                if (row.expiresAt() != null && row.expiresAt().isBefore(java.time.Instant.now())) {
                    return new GuestRedeemResult(row, "expired");
                }
                if (row.waitingRoom() && row.admittedAt() == null) {
                    return new GuestRedeemResult(row, "waiting");
                }
                return new GuestRedeemResult(row, "ready");
            });
    }

    public List<Phase5AdrRepository.GuestLinkLookupRow> listWaitingGuests(
        UUID chatId, UUID conferenceId, UUID userId
    ) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return List.of();
        }
        return repository.listWaitingGuestLinks(conferenceId);
    }

    public Optional<Boolean> admitGuest(UUID chatId, UUID conferenceId, UUID userId, UUID linkId) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId) || linkId == null) {
            return Optional.empty();
        }
        return repository.admitGuestLink(linkId, conferenceId, chatId)
            ? Optional.of(Boolean.TRUE)
            : Optional.empty();
    }

    public Optional<Phase5AdrRepository.SipGatewayRow> sipStatus(UUID userId) {
        return orgIdForUser(userId).flatMap(repository::getSipGateway);
    }

    public Optional<Phase5AdrRepository.SipGatewayRow> upsertSip(UUID userId, boolean enabled, String uri, boolean h323) {
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null) {
            return Optional.empty();
        }
        return Optional.of(repository.upsertSipGateway(orgId, enabled, uri, h323));
    }

    public List<Phase5AdrRepository.PasskeyRow> listPasskeys(UUID userId) {
        return resolvePasskeyUserId(userId)
            .map(repository::listPasskeys)
            .orElse(List.of());
    }

    public Optional<UUID> registerPasskeyScaffold(UUID userId, String credentialId, String publicKey) {
        if (credentialId == null || credentialId.isBlank()) {
            return Optional.empty();
        }
        return resolvePasskeyUserId(userId)
            .map(uid -> repository.registerPasskeyScaffold(uid, credentialId.trim(), publicKey));
    }

    private Optional<UUID> resolvePasskeyUserId(UUID jwtUserId) {
        if (userLookupPort.findById(jwtUserId).isPresent()) {
            return Optional.of(jwtUserId);
        }
        var byExternal = userLookupPort.findByExternalId(jwtUserId.toString())
            .map(p -> UUID.fromString(p.id()));
        if (byExternal.isPresent()) {
            return byExternal;
        }
        return userLookupPort.findByUsername("admin")
            .map(p -> UUID.fromString(p.id()));
    }

    public Optional<AiAssistResult> aiAssist(UUID chatId, UUID userId, String prompt) {
        if (!isChatMember(chatId, userId) || prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null) {
            return Optional.empty();
        }
        var instance = pluginRepository.listInstances(orgId).stream()
            .filter(i -> i.enabled() && AI_PRESET.equals(i.presetId()))
            .findFirst();
        if (instance.isEmpty()) {
            return Optional.of(new AiAssistResult("mock", "AI gateway not configured; enable ai-chat-gateway preset."));
        }
        var row = instance.get();
        var event = new PluginEvent(
            UUID.randomUUID().toString(),
            row.id(),
            row.pluginClass(),
            "ai.assist",
            userId,
            chatId,
            prompt,
            Map.of("chat_id", chatId.toString()),
            null);
        var result = pluginPlatformService.invoke(row.id(), event);
        if (result.outcome() != PluginPlatformService.InvokeOutcome.SUCCESS || result.response() == null) {
            return Optional.of(new AiAssistResult("error", "AI gateway invoke failed"));
        }
        var messages = result.response().messages();
        var text = messages != null && !messages.isEmpty() ? messages.get(0).text() : "";
        return Optional.of(new AiAssistResult("ok", text != null ? text : ""));
    }

    public Optional<Phase5AdrRepository.CaptionSessionRow> startCaptions(
        UUID chatId, UUID conferenceId, UUID userId, String language, String sampleText
    ) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return Optional.empty();
        }
        var session = repository.startCaptions(conferenceId, chatId, language);
        var transcript = invokeStt(userId, chatId, sampleText != null ? sampleText : "hello world");
        if (transcript != null) {
            repository.appendCaptionTranscript(session.id(), transcript);
            return repository.getLatestCaptionSession(conferenceId);
        }
        return Optional.of(session);
    }

    public Optional<Phase5AdrRepository.CaptionSessionRow> getCaptions(
        UUID chatId, UUID conferenceId, UUID userId
    ) {
        if (!isChatMember(chatId, userId) || !conferenceInChat(conferenceId, chatId)) {
            return Optional.empty();
        }
        return repository.getLatestCaptionSession(conferenceId);
    }

    private String invokeStt(UUID userId, UUID chatId, String text) {
        var orgId = orgIdForUser(userId).orElse(null);
        if (orgId == null) {
            return null;
        }
        var instance = pluginRepository.listInstances(orgId).stream()
            .filter(i -> i.enabled() && STT_PRESET.equals(i.presetId()))
            .findFirst();
        if (instance.isEmpty()) {
            try {
                return MAPPER.writeValueAsString(Map.of("lines", List.of(Map.of("text", text, "source", "local"))));
            } catch (Exception e) {
                return null;
            }
        }
        var row = instance.get();
        var event = new PluginEvent(
            UUID.randomUUID().toString(),
            row.id(),
            row.pluginClass(),
            "stt.transcribe",
            userId,
            chatId,
            text,
            Map.of(),
            null);
        var result = pluginPlatformService.invoke(row.id(), event);
        if (result.outcome() != PluginPlatformService.InvokeOutcome.SUCCESS || result.response() == null) {
            return null;
        }
        var messages = result.response().messages();
        if (messages != null && !messages.isEmpty()) {
            try {
                return MAPPER.writeValueAsString(Map.of("lines", List.of(Map.of("text", messages.get(0).text()))));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public record AiAssistResult(String status, String reply) {}

    public record GuestRedeemResult(Phase5AdrRepository.GuestLinkLookupRow link, String status) {
        public UUID conferenceId() {
            return link.conferenceId();
        }

        public UUID chatId() {
            return link.chatId();
        }

        public boolean waitingRoom() {
            return link.waitingRoom();
        }
    }
}
