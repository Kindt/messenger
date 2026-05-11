package com.avandocmsg.messenger.common.dto;

import com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Публикуется в {@link com.avandocmsg.messenger.common.nats.NatsSubjects#MSG_EVENT_RETENTION} после успешной
 * очистки тела сообщения в Hot DB (воркер ретенции). Без полного текста сообщения — только метаданные и длина.
 * <p>
 * JSON {@code snapshot_version}: при отсутствии поля в старых сообщениях десериализация подставляет
 * {@link ArchiveSnapshotFormat#SNAPSHOT_VERSION} (сейчас {@code 1}), как у снимка в MinIO; явное {@code 0}
 * в payload допускается.
 * <p>
 * JSON {@code pass_id}: опционально — один UUID на проход {@code RetentionHotBodyJanitor.runOnce}; в старых
 * сообщениях поле может отсутствовать ({@code null} при десериализации).
 * <p>
 * JSON {@code snapshot_sha256}: опционально — SHA-256 снимка в MinIO (см. {@link ArchiveSnapshotFormat#JSON_SNAPSHOT_SHA256}
 * и документацию воркера); в старых сообщениях поле может отсутствовать ({@code null}). При {@code RETENTION_REQUIRE_MINIO=false}
 * и отключённом MinIO в воркере — {@code null}. В режиме {@code RETENTION_DRY_RUN=true} событие не публикуется.
 */
public record RetentionAppliedEvent(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("action") String action,
    @JsonProperty("applied_at_epoch_ms") long appliedAtEpochMs,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("storage_object_key") String storageObjectKey,
    @JsonProperty("cleared_content_utf8_bytes") int clearedContentUtf8Bytes,
    @JsonProperty("snapshot_version") int snapshotVersion,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("pass_id") String passId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("snapshot_sha256") String snapshotSha256
) {
    public static final String ACTION_HOT_BODY_CLEARED = "hot_body_cleared";

    @JsonCreator
    public static RetentionAppliedEvent fromJson(
        @JsonProperty("message_id") String messageId,
        @JsonProperty("chat_id") String chatId,
        @JsonProperty("action") String action,
        @JsonProperty("applied_at_epoch_ms") long appliedAtEpochMs,
        @JsonProperty("storage_object_key") String storageObjectKey,
        @JsonProperty("cleared_content_utf8_bytes") int clearedContentUtf8Bytes,
        @JsonProperty("snapshot_version") Integer snapshotVersion,
        @JsonProperty("pass_id") String passId,
        @JsonProperty("snapshot_sha256") String snapshotSha256
    ) {
        return new RetentionAppliedEvent(
            messageId,
            chatId,
            action,
            appliedAtEpochMs,
            storageObjectKey,
            clearedContentUtf8Bytes,
            snapshotVersion != null ? snapshotVersion : ArchiveSnapshotFormat.SNAPSHOT_VERSION,
            passId,
            snapshotSha256
        );
    }

    public static RetentionAppliedEvent hotBodyCleared(
        String messageId,
        String chatId,
        String storageObjectKey,
        int clearedContentUtf8Bytes,
        long appliedAtEpochMs
    ) {
        return hotBodyCleared(
            messageId,
            chatId,
            storageObjectKey,
            clearedContentUtf8Bytes,
            appliedAtEpochMs,
            ArchiveSnapshotFormat.SNAPSHOT_VERSION,
            null,
            null
        );
    }

    public static RetentionAppliedEvent hotBodyCleared(
        String messageId,
        String chatId,
        String storageObjectKey,
        int clearedContentUtf8Bytes,
        long appliedAtEpochMs,
        int snapshotVersion
    ) {
        return hotBodyCleared(
            messageId,
            chatId,
            storageObjectKey,
            clearedContentUtf8Bytes,
            appliedAtEpochMs,
            snapshotVersion,
            null,
            null
        );
    }

    public static RetentionAppliedEvent hotBodyCleared(
        String messageId,
        String chatId,
        String storageObjectKey,
        int clearedContentUtf8Bytes,
        long appliedAtEpochMs,
        int snapshotVersion,
        String passId
    ) {
        return hotBodyCleared(
            messageId,
            chatId,
            storageObjectKey,
            clearedContentUtf8Bytes,
            appliedAtEpochMs,
            snapshotVersion,
            passId,
            null
        );
    }

    public static RetentionAppliedEvent hotBodyCleared(
        String messageId,
        String chatId,
        String storageObjectKey,
        int clearedContentUtf8Bytes,
        long appliedAtEpochMs,
        int snapshotVersion,
        String passId,
        String snapshotSha256
    ) {
        return new RetentionAppliedEvent(
            messageId,
            chatId,
            ACTION_HOT_BODY_CLEARED,
            appliedAtEpochMs,
            storageObjectKey,
            clearedContentUtf8Bytes,
            snapshotVersion,
            passId,
            snapshotSha256
        );
    }
}
