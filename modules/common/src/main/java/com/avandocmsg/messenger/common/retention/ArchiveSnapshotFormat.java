package com.avandocmsg.messenger.common.retention;

/**
 * Shared envelope for JSON objects written to MinIO by {@code retention-worker} (body snapshots) and
 * {@code DeepArchiverWorker} ({@code messages/{messageId}.json}). Consumers may branch on {@link #SNAPSHOT_VERSION}.
 */
public final class ArchiveSnapshotFormat {

    /** Increment when the snapshot JSON shape changes in a breaking way. */
    public static final int SNAPSHOT_VERSION = 1;

    public static final String PRODUCER_RETENTION = "retention-worker";

    public static final String PRODUCER_DEEP_ARCHIVER = "deep-archiver";

    public static final String JSON_SNAPSHOT_VERSION = "snapshot_version";

    public static final String JSON_PRODUCER = "producer";

    /**
     * MinIO JSON (retention hot-body snapshot and {@code DeepArchiverWorker} {@code messages/{id}.json}): lowercase
     * hex SHA-256 of the UTF-8 bytes produced by serializing the same root object <strong>before</strong> this property
     * is added (same {@code ObjectMapper} as upload — see {@code docs/RETENTION_AND_DEEP_ARCHIVE.md} §6–§7).
     */
    public static final String JSON_SNAPSHOT_SHA256 = "snapshot_sha256";

    // --- chunk constants ---

    /** Filename of the chunk manifest within a chunked message directory. */
    public static final String CHUNK_MANIFEST_FILENAME = "manifest.json";

    /** Prefix for chunk part files. */
    public static final String CHUNK_PART_PREFIX = "part-";

    /** Format for chunk part filenames (zero-padded index). */
    public static final String CHUNK_PART_FORMAT = "part-%03d.json";

    /** Default chunk size threshold (10 MiB). */
    public static final int DEFAULT_CHUNK_SIZE_BYTES = 10 * 1024 * 1024;

    private ArchiveSnapshotFormat() {
    }
}
