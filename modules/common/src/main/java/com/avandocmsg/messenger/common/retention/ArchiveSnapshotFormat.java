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

    private ArchiveSnapshotFormat() {
    }
}
