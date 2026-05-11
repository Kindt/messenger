package com.avandocmsg.messenger.worker.retention;

import java.util.UUID;

/**
 * Pure helpers for deciding when retention may skip uploading a JSON body snapshot because an object
 * already exists (same key layout as the {@code deep-archiver} worker {@code messages/{id}.json}, or a prior
 * retention snapshot in this bucket).
 */
final class RetentionSnapshotSkipResolver {

    private RetentionSnapshotSkipResolver() {
    }

    /**
     * Object key used by {@code DeepArchiverWorker} for indexed events (same bucket as {@code MINIO_BUCKET}).
     */
    static String deepArchiveObjectKey(UUID messageId) {
        return "messages/" + messageId + ".json";
    }

    /**
     * {@code true} when retention writes to the same bucket deep-archiver uses ({@code MINIO_BUCKET} /
     * {@link RetentionPlatformDefaults#minioBucketFromEnv()}), so a HEAD on {@link #deepArchiveObjectKey} is meaningful.
     */
    static boolean sameBucketAsDeepArchive(String retentionWriteBucket, String minioBucketDefault) {
        if (retentionWriteBucket == null || minioBucketDefault == null) {
            return false;
        }
        return retentionWriteBucket.equals(minioBucketDefault);
    }
}
