package com.avandocmsg.messenger.common.retention;

import com.avandocmsg.messenger.common.dto.ChunkEntry;
import com.avandocmsg.messenger.common.dto.DeepArchiveManifest;
import com.avandocmsg.messenger.common.util.Sha256Hex;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;

/**
 * Shared chunked snapshot writer (parts + manifest) for deep-archive and retention snapshots.
 */
public final class ChunkedSnapshotWriter {

    private ChunkedSnapshotWriter() {
    }

    /**
     * Writes chunk parts and a manifest under {@code objectPrefixDir}.
     *
     * @return number of chunk parts written
     */
    public static int writeChunkedSnapshot(
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        byte[] bytes,
        int chunkSizeBytes,
        ObjectMapper mapper
    ) throws Exception {
        int chunkSize = Math.max(1, chunkSizeBytes);
        var chunks = new ArrayList<ChunkEntry>();
        int offset = 0;
        int partIndex = 0;
        while (offset < bytes.length) {
            int end = Math.min(offset + chunkSize, bytes.length);
            var partBytes = new byte[end - offset];
            System.arraycopy(bytes, offset, partBytes, 0, partBytes.length);
            var partName = String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, partIndex);
            var sha256 = Sha256Hex.of(partBytes);
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPrefixDir + partName)
                    .stream(new ByteArrayInputStream(partBytes), partBytes.length, -1)
                    .contentType("application/json")
                    .build()
            );
            chunks.add(new ChunkEntry(partName, partIndex, partBytes.length, sha256));
            offset = end;
            partIndex++;
        }
        long totalSize = bytes.length;
        var totalSha256 = Sha256Hex.of(bytes);
        var manifest = new DeepArchiveManifest(messageId, chunks.size(), chunks, totalSize, totalSha256);
        var manifestBytes = mapper.writeValueAsBytes(manifest);
        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectPrefixDir + ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME)
                .stream(new ByteArrayInputStream(manifestBytes), manifestBytes.length, -1)
                .contentType("application/json")
                .build()
        );
        return chunks.size();
    }

    /**
     * Streams a file into chunk parts without loading the full payload into heap.
     *
     * @return number of chunk parts written
     */
    public static int writeChunkedSnapshotFromFile(
        MinioClient client,
        String bucket,
        String objectPrefixDir,
        String messageId,
        Path file,
        int chunkSizeBytes,
        ObjectMapper mapper
    ) throws Exception {
        int chunkSize = Math.max(1, chunkSizeBytes);
        long totalSize = Files.size(file);
        var chunks = new ArrayList<ChunkEntry>();
        var totalDigest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[chunkSize];
            int partIndex = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                byte[] partBytes = read == buf.length ? buf.clone() : java.util.Arrays.copyOf(buf, read);
                totalDigest.update(partBytes);
                var partName = String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, partIndex);
                var sha256 = Sha256Hex.of(partBytes);
                client.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPrefixDir + partName)
                        .stream(new ByteArrayInputStream(partBytes), partBytes.length, -1)
                        .contentType("application/json")
                        .build()
                );
                chunks.add(new ChunkEntry(partName, partIndex, partBytes.length, sha256));
                partIndex++;
            }
        }
        var totalSha256 = HexFormat.of().withLowerCase().formatHex(totalDigest.digest());
        var manifest = new DeepArchiveManifest(messageId, chunks.size(), chunks, totalSize, totalSha256);
        var manifestBytes = mapper.writeValueAsBytes(manifest);
        client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectPrefixDir + ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME)
                .stream(new ByteArrayInputStream(manifestBytes), manifestBytes.length, -1)
                .contentType("application/json")
                .build()
        );
        return chunks.size();
    }
}
