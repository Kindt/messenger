package com.avandocmsg.messenger.common.nats;

/**
 * Central NATS subject names for messaging workers (ТЗ п. 6, pipeline / fan-out).
 * Контракт для gateway и воркеров: {@code docs/NATS_SUBJECTS_INTEROP.md}.
 */
public final class NatsSubjects {
    public static final String MSG_SEND = "msg.send";
    public static final String MSG_DELIVER_PREFIX = "msg.deliver.";

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

    /** Конференция в чате (JSON {@link com.avandocmsg.messenger.common.dto.ConferenceChangeEvent}). */
    public static final String MSG_CONFERENCE = "msg.conference";

    /** Metadata-only downstream events (JSON {@link com.avandocmsg.messenger.common.dto.MessageWorkerEvent}). */
    public static final String MSG_EVENT_INDEX = "msg.event.index";
    public static final String MSG_EVENT_PUSH = "msg.event.push";
    public static final String MSG_EVENT_BOT = "msg.event.bot";

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

    private NatsSubjects() {
    }
}
