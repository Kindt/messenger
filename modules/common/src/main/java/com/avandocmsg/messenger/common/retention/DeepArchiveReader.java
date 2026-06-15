package com.avandocmsg.messenger.common.retention;

import com.avandocmsg.messenger.common.dto.DeepArchiveManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;

public class DeepArchiveReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeepArchiveReader() {
    }

    /**
     * Reads a deep-archived message from MinIO. Supports both flat
     * ({@code messages/{messageId}.json}) and chunked ({@code messages/{messageId}/manifest.json}
     * + {@code part-NNN.json}) formats.
     *
     * @return an InputStream of the reconstructed message JSON, or empty if not found
     */
    public static Optional<InputStream> readMessage(MinioClient client, String bucket, String messageId) {
        try {
            var dir = "messages/" + messageId + "/";
            var manifestKey = dir + ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME;
            try (var manifestStream = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(manifestKey).build())) {
                var manifest = MAPPER.readValue(manifestStream, DeepArchiveManifest.class);
                var out = new ByteArrayOutputStream();
                for (var chunk : manifest.chunks()) {
                    try (var partStream = client.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(dir + chunk.partName()).build())) {
                        var stored = partStream.readAllBytes();
                        out.write(SnapshotPartCodec.decompress(stored));
                    }
                }
                return Optional.of(new ByteArrayInputStream(out.toByteArray()));
            }
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return readFlat(client, bucket, messageId);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<InputStream> readFlat(MinioClient client, String bucket, String messageId) {
        try {
            var flatKey = "messages/" + messageId + ".json";
            var stream = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(flatKey).build());
            var stored = stream.readAllBytes();
            stream.close();
            return Optional.of(new ByteArrayInputStream(SnapshotPartCodec.decompress(stored)));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
