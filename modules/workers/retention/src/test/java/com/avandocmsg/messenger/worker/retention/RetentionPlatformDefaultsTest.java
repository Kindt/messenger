package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RetentionPlatformDefaultsTest {

    @Test
    void batchLimitFromEnv_default25WhenUnset() {
        assumeTrue(System.getenv("RETENTION_BATCH_LIMIT") == null);
        assertEquals(25, RetentionPlatformDefaults.batchLimitFromEnv());
    }

    @Test
    void jdbcLooksLikePostgres() {
        assertTrue(RetentionPlatformDefaults.jdbcLooksLikePostgres("jdbc:postgresql://localhost:5432/hot"));
        assertFalse(RetentionPlatformDefaults.jdbcLooksLikePostgres("jdbc:h2:mem:x"));
    }

    @Test
    void fromEnv_matchesAppConfigDefaultsWhenEnvUnset() {
        assumeTrue(System.getenv("RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS") == null);
        assumeTrue(System.getenv("RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED") == null);
        assumeTrue(System.getenv("RETENTION_DEFAULT_LEGAL_HOLD") == null);
        var d = RetentionPlatformDefaults.fromEnv();
        assertNull(d.hotBodyMaxAgeDays());
        assertTrue(d.defaultDeepArchiveEnabled());
        assertFalse(d.defaultLegalHold());
    }

    @Test
    void auditEnabledFromEnv_defaultTrueWhenUnset() {
        assumeTrue(System.getenv("RETENTION_AUDIT_ENABLED") == null);
        assertTrue(RetentionPlatformDefaults.auditEnabledFromEnv());
    }

    @Test
    void normalizeRetentionObjectPrefix_defaultsAndTrailingSlash() {
        assertEquals("retention/body/", RetentionPlatformDefaults.normalizeRetentionObjectPrefix(null));
        assertEquals("retention/body/", RetentionPlatformDefaults.normalizeRetentionObjectPrefix(""));
        assertEquals("archive/msg/", RetentionPlatformDefaults.normalizeRetentionObjectPrefix("/archive/msg"));
        assertEquals("p/", RetentionPlatformDefaults.normalizeRetentionObjectPrefix("p"));
    }

    @Test
    void retentionWriteBucketFromEnv_overrideWhenSet() {
        assumeTrue(System.getenv("RETENTION_MINIO_BUCKET") == null);
        assertEquals("my-bucket", RetentionPlatformDefaults.retentionWriteBucketFromEnv("my-bucket"));
    }

    @Test
    void ensureMinioBucketFromEnv_defaultTrueWhenUnset() {
        assumeTrue(System.getenv("RETENTION_ENSURE_MINIO_BUCKET") == null);
        assertTrue(RetentionPlatformDefaults.ensureMinioBucketFromEnv());
    }

    @Test
    void initialDelaySecondsFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_INITIAL_DELAY_SECONDS") == null);
        assertEquals(0, RetentionPlatformDefaults.initialDelaySecondsFromEnv());
    }

    @Test
    void metricsPortFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_METRICS_PORT") == null);
        assertEquals(0, RetentionPlatformDefaults.metricsPortFromEnv());
    }

    @Test
    void bulkAuditMinClearedFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_BULK_AUDIT_MIN_CLEARED") == null);
        assertEquals(0, RetentionPlatformDefaults.bulkAuditMinClearedFromEnv());
    }

    @Test
    void skipSnapshotIfDeepExistsFromEnv_defaultFalseWhenUnset() {
        assumeTrue(System.getenv("RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS") == null);
        assertFalse(RetentionPlatformDefaults.skipSnapshotIfDeepExistsFromEnv());
    }

    @Test
    void dryRunFromEnv_defaultFalseWhenUnset() {
        assumeTrue(System.getenv("RETENTION_DRY_RUN") == null);
        assertFalse(RetentionPlatformDefaults.dryRunFromEnv());
    }

    @Test
    void useAdvisoryLockFromEnv_defaultFalseWhenUnset() {
        assumeTrue(System.getenv("RETENTION_USE_ADVISORY_LOCK") == null);
        assertFalse(RetentionPlatformDefaults.useAdvisoryLockFromEnv());
    }

    @Test
    void useAdvisoryLockFromRaw_parsesBoolean() {
        assertFalse(RetentionPlatformDefaults.useAdvisoryLockFromRaw(null));
        assertFalse(RetentionPlatformDefaults.useAdvisoryLockFromRaw(""));
        assertFalse(RetentionPlatformDefaults.useAdvisoryLockFromRaw("false"));
        assertTrue(RetentionPlatformDefaults.useAdvisoryLockFromRaw("true"));
    }

    @Test
    void jdbcQueryTimeoutSecondsFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_JDBC_QUERY_TIMEOUT_SECONDS") == null);
        assertEquals(0, RetentionPlatformDefaults.jdbcQueryTimeoutSecondsFromEnv());
    }

    @Test
    void interMessageDelayMsFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_INTER_MESSAGE_DELAY_MS") == null);
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMsFromEnv());
    }

    @Test
    void snapshotTempfileThresholdBytesFromEnv_defaultZeroWhenUnset() {
        assumeTrue(System.getenv("RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES") == null);
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromEnv());
    }

    @Test
    void minioMultipartThresholdBytesFromEnv_defaultMaxWhenUnset() {
        assumeTrue(System.getenv("RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES") == null);
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromEnv()
        );
    }

    @Test
    void minioMultipartThresholdBytesFromRaw_parsesPositiveOrFallsBackToDefault() {
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw(null)
        );
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("")
        );
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("0")
        );
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("-5")
        );
        assertEquals(
            RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT,
            RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("not-a-number")
        );
        assertEquals(33_554_432L, RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("33554432"));
        assertEquals(1L, RetentionPlatformDefaults.minioMultipartThresholdBytesFromRaw("1"));
    }

    @Test
    void snapshotTempfileThresholdBytesFromRaw_clampsParsesAndRejectsInvalid() {
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw(null));
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw(""));
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw("  "));
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw("-1"));
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw("not-a-number"));
        assertEquals(1L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw("1"));
        assertEquals(0L, RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw("9999999999999999999"));
        assertEquals(
            RetentionPlatformDefaults.SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX,
            RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw(String.valueOf(Long.MAX_VALUE))
        );
        assertEquals(
            RetentionPlatformDefaults.SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX,
            RetentionPlatformDefaults.snapshotTempfileThresholdBytesFromRaw(
                String.valueOf(RetentionPlatformDefaults.SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX + 1)
            )
        );
    }

    @Test
    void interMessageDelayMillisFromRaw_clampsAndParses() {
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw(null));
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw(""));
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("  "));
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("-1"));
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("0"));
        assertEquals(1, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("1"));
        assertEquals(60_000, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("60000"));
        assertEquals(60_000, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("60001"));
        assertEquals(60_000, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("999999999"));
        assertEquals(0, RetentionPlatformDefaults.interMessageDelayMillisFromRaw("not-a-number"));
    }
}
