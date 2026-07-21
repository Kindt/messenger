package com.avandocmsg.messenger.common.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;

import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * Facade for chunked snapshot writes (PS-3.1): shared threshold/prefix helpers + delegates to
 * {@link ChunkedSnapshotWriter}.
 */
public final class ChunkManifestWriter {

    private ChunkManifestWriter() {
    }

    /** When {@code thresholdBytes > 0} and payload exceeds threshold. */
    public static boolean shouldWriteChunked(long thresholdBytes, long payloadBytes) {
        return thresholdBytes > 0 && payloadBytes > thresholdBytes;
    }

    public static String objectPrefixDir(String basePrefix, String messageId) {
        var prefix = basePrefix != null ? basePrefix : "";
        return prefix + messageId + "/";
    }

    public static int resolveChunkSizeBytes(long chunkThreshold) {
        if (chunkThreshold > 0) {
            return (int) Math.min(chunkThreshold, Integer.MAX_VALUE);
        }
        return ArchiveSnapshotFormat.DEFAULT_CHUNK_SIZE_BYTES;
    }

    public static int writeChunkedSnapshot(
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        byte[] bytes,
        int chunkSizeBytes,
        ObjectMapper mapper
    ) throws Exception {
        return ChunkedSnapshotWriter.writeChunkedSnapshot(
            client, bucket, objectPrefixDir, messageId, bytes, chunkSizeBytes, mapper);
    }

    public static int writeChunkedSnapshot( // NOSONAR java:S107 -- facade mirrors ChunkedSnapshotWriter public API
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        byte[] bytes,
        int chunkSizeBytes,
        ObjectMapper mapper,
        SnapshotCompression compression,
        int zstdLevel,
        LongConsumer bytesSavedRecorder
    ) throws Exception {
        return ChunkedSnapshotWriter.writeChunkedSnapshot(
            client, bucket, objectPrefixDir, messageId, bytes, chunkSizeBytes, mapper,
            compression, zstdLevel, bytesSavedRecorder);
    }

    public static int writeChunkedSnapshotFromFile(
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        Path file,
        int chunkSizeBytes,
        ObjectMapper mapper
    ) throws Exception {
        return ChunkedSnapshotWriter.writeChunkedSnapshotFromFile(
            client, bucket, objectPrefixDir, messageId, file, chunkSizeBytes, mapper);
    }

    public static int writeChunkedSnapshotFromFile( // NOSONAR java:S107 -- facade mirrors ChunkedSnapshotWriter public API
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        Path file,
        int chunkSizeBytes,
        ObjectMapper mapper,
        SnapshotCompression compression,
        int zstdLevel,
        LongConsumer bytesSavedRecorder
    ) throws Exception {
        return ChunkedSnapshotWriter.writeChunkedSnapshotFromFile(
            client, bucket, objectPrefixDir, messageId, file, chunkSizeBytes, mapper,
            compression, zstdLevel, bytesSavedRecorder);
    }

    public static byte[] compressFlatSnapshot(byte[] bytes, SnapshotCompression compression, int zstdLevel) {
        return ChunkedSnapshotWriter.compressFlatSnapshot(bytes, compression, zstdLevel);
    }
}
