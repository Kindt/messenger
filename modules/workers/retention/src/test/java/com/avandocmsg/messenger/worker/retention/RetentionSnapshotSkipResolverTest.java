package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionSnapshotSkipResolverTest {

    @Test
    void deepArchiveObjectKey_matchesDeepArchiverLayout() {
        var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals("messages/11111111-1111-1111-1111-111111111111.json", RetentionSnapshotSkipResolver.deepArchiveObjectKey(id));
    }

    @Test
    void sameBucketAsDeepArchive_trueWhenNamesEqual() {
        assertTrue(RetentionSnapshotSkipResolver.sameBucketAsDeepArchive("deep-archive", "deep-archive"));
    }

    @Test
    void sameBucketAsDeepArchive_falseWhenDifferentOrNull() {
        assertFalse(RetentionSnapshotSkipResolver.sameBucketAsDeepArchive("retention-only", "deep-archive"));
        assertFalse(RetentionSnapshotSkipResolver.sameBucketAsDeepArchive(null, "x"));
        assertFalse(RetentionSnapshotSkipResolver.sameBucketAsDeepArchive("x", null));
    }
}
