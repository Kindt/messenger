package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Shared MinIO JSON object read for export-replay snapshot sources. */
final class ExportMinioJsonFetcher {

    private static final Logger log = LoggerFactory.getLogger(ExportMinioJsonFetcher.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ExportMinioJsonFetcher() {
    }

    static Optional<ObjectNode> fetchSnapshot(
        MinioClient client,
        String bucket,
        String objectKey,
        String messageId,
        String source,
        UserMessageSource workerMessages
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            try (var in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                var snapshot = MAPPER.readTree(in);
                var out = MAPPER.createObjectNode();
                out.put("messageId", messageId);
                out.put("source", source);
                out.put("objectKey", objectKey);
                out.put("bucket", bucket);
                out.set("snapshot", snapshot);
                return Optional.of(out);
            }
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            log.debug(workerMessages.format("worker.export_replay.minio_stat_failed", source, messageId, objectKey, e.getMessage()));
            return Optional.empty();
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.export_replay.minio_read_failed",
                source, messageId, bucket, objectKey, e.getMessage()));
            return Optional.empty();
        }
    }
}
