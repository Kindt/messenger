package com.avandocmsg.messenger.common.nats;

/**
 * Central NATS subject names for messaging workers (ТЗ п. 6, pipeline / fan-out).
 * Контракт для gateway и воркеров: {@code docs/NATS_SUBJECTS_INTEROP.md}.
 */
public final class NatsSubjects {
    public static final String MSG_SEND = "msg.send";
    /** Dead-letter subject for failed {@link #MSG_SEND} after max deliver attempts. */
    public static final String MSG_SEND_DLQ = "msg.send.dlq";
    public static final String MSG_DELIVER_PREFIX = "msg.deliver.";
    /** Large-chat broadcast: one publish per event (PS-1.3). */
    public static final String MSG_DELIVER_CHAT_PREFIX = "msg.deliver.chat.";

    public static String deliverUserSubject(String userId) {
        return MSG_DELIVER_PREFIX + userId;
    }

    public static String deliverChatSubject(String chatId) {
        return MSG_DELIVER_CHAT_PREFIX + chatId;
    }

    /**
     * WebRTC signaling ingress: JSON {@link com.avandocmsg.messenger.common.dto.RtcSignalEvent} (поле {@code payload} — SDP / ICE и т.д.).
     * Публикует ws-gateway; fan-out в {@link #MSG_DELIVER_PREFIX} делает message-pipeline после проверки членства в чате.
     */
    public static final String RTC_SIGNAL = "rtc.signal";

    /** Клиент печатает (метаданные JSON {@link com.avandocmsg.messenger.common.dto.TypingEvent}). */
    public static final String MSG_TYPING = "msg.typing";

    /** Правка или мягкое удаление сообщения (JSON {@link com.avandocmsg.messenger.common.dto.MessageChangeEvent}). */
    public static final String MSG_CHANGE = "msg.change";

    /** Реакция на сообщение (JSON {@link com.avandocmsg.messenger.common.dto.ReactionChangeEvent}). */
    public static final String MSG_REACTION = "msg.reaction";

    /** Закрепление сообщения (JSON {@link com.avandocmsg.messenger.common.dto.PinChangeEvent}). */
    public static final String MSG_PIN = "msg.pin";

    /** @mention уведомление (JSON {@link com.avandocmsg.messenger.common.dto.MentionEvent}). */
    public static final String MSG_MENTION = "msg.mention";

    /** Конференция в чате (JSON {@link com.avandocmsg.messenger.common.dto.ConferenceChangeEvent}). */
    public static final String MSG_CONFERENCE = "msg.conference";

    /** Live-streaming session (JSON {@link com.avandocmsg.messenger.common.dto.LiveSessionChangeEvent}). */
    public static final String LIVE_SESSION = "live.session";

    /**
     * Legacy per-consumer subject for metadata-only downstream events
     * (JSON {@link com.avandocmsg.messenger.common.dto.MessageWorkerEvent}); retained for core-api
     * hot-plug indexer publish, retention workers, and {@code NATS_DOWNSTREAM_LEGACY_PUBLISH=true}
     * rollback (spec 025 FR-012 Phase 3).
     */
    public static final String MSG_EVENT_INDEX = "msg.event.index";
    /**
     * @deprecated Phase 3: use {@link #MSG_EVENT_DOWNSTREAM} with route {@code push}; kept for legacy publish rollback.
     */
    @Deprecated
    public static final String MSG_EVENT_PUSH = "msg.event.push"; // NOSONAR java:S1133 — retained for NATS_DOWNSTREAM_LEGACY_PUBLISH rollback
    /**
     * @deprecated Phase 3: use {@link #MSG_EVENT_DOWNSTREAM} with route {@code bot}; kept for legacy publish rollback.
     */
    @Deprecated
    public static final String MSG_EVENT_BOT = "msg.event.bot"; // NOSONAR java:S1133 — retained for NATS_DOWNSTREAM_LEGACY_PUBLISH rollback

    /**
     * Consolidated downstream envelope ({@link com.avandocmsg.messenger.common.dto.MessageDownstreamEvent}).
     * Phase 3 default publish path; legacy triple-publish gated by {@code NATS_DOWNSTREAM_LEGACY_PUBLISH}.
     */
    public static final String MSG_EVENT_DOWNSTREAM = "msg.event.downstream";

    /** Archiver → deep-archiver handoff (JSON {@link com.avandocmsg.messenger.common.dto.MessageWorkerEvent}). */
    public static final String MSG_EVENT_DEEP_ARCHIVE = "msg.event.deep-archive";

    /**
     * Факт применения ретенции к сообщению в Hot DB (JSON {@link com.avandocmsg.messenger.common.dto.RetentionAppliedEvent}).
     * Подписчики: аудит, метрики, будущие интеграции; не дублирует полезную нагрузку {@link #MSG_EVENT_DEEP_ARCHIVE}.
     */
    public static final String MSG_EVENT_RETENTION = "msg.event.retention";

    /** Export / compliance replay triggers (JSON {@link com.avandocmsg.messenger.common.dto.ExportReplayJob}). */
    public static final String MSG_EXPORT_REPLAY = "msg.export.replay";

    /** Export job completion (JSON {@link com.avandocmsg.messenger.common.dto.ExportReplayCompleteEvent}). */
    public static final String MSG_EXPORT_REPLAY_COMPLETE = "msg.export.replay.complete";

    /**
     * Retention (or other subsystem) suggests running chat export before destructive retention
     * (JSON {@link com.avandocmsg.messenger.common.dto.ExportSuggestedEvent}).
     */
    public static final String MSG_EXPORT_SUGGESTED = "msg.export.suggested";

    /**
     * Export job cancelled (JSON {@link com.avandocmsg.messenger.common.dto.ExportReplayCancelEvent}).
     * Workers still rely on {@code export_jobs.status = export_cancelled}; this is an optional fast hint.
     */
    public static final String MSG_EXPORT_REPLAY_CANCEL = "msg.export.replay.cancel";

    /** Hot-plug heartbeat/lifecycle subjects for extracted services (experimental; see interop docs). */
    public static final String SVC_HEARTBEAT_PREFIX = "$SVC.heartbeat.";
    public static final String SVC_HEARTBEAT_WILDCARD = "$SVC.heartbeat.*";
    public static final String SVC_LIFECYCLE_PREFIX = "$SVC.lifecycle.";

    /** Per-participant read receipt (JSON {@link com.avandocmsg.messenger.common.dto.ReadReceiptEvent}). */
    public static final String MSG_READ_RECEIPT = "msg.read_receipt";

    /** User profile presence/custom status fan-out (spec 022). */
    public static final String USER_PRESENCE = "user.presence";

    /**
     * Legacy read-cache invalidation via NATS (JSON {@link com.avandocmsg.messenger.common.dto.ReadCacheInvalidateEvent}).
     *
     * @deprecated spec 025 FR-009: message-pipeline writes Redis DEL directly; subscriber kept for rollback only.
     */
    @Deprecated
    public static final String MSG_CACHE_INVALIDATE = "msg.cache.invalidate"; // NOSONAR java:S1133 — retained for rollback until Redis-direct path is mandatory

    /**
     * MLS Welcome wire payload (binary KMLS envelope — {@link com.avandocmsg.messenger.api.mls.wire.MlsWireCodec}).
     * Публикует core-api {@code MlsWirePublisher} при создании MLS-группы.
     */
    public static final String MLS_WELCOME = "mls.welcome";

    /**
     * MLS Commit wire payload (binary KMLS envelope).
     * Публикует core-api при add/remove участника группы.
     */
    public static final String MLS_COMMIT = "mls.commit";

    /**
     * MLS epoch bump notification (binary KMLS envelope).
     * Публикует core-api после commit / смены эпохи.
     */
    public static final String MLS_EPOCH = "mls.epoch";

    private NatsSubjects() {
    }
}
