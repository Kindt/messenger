package com.avandocmsg.messenger.worker.push;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushWorkerMentionBatchTest {

    @Test
    void isUserMentioned_usesPrefetchedSet() {
        var uid = UUID.randomUUID();
        var mentioned = Set.of(uid);
        assertTrue(PushWorker.isUserMentionedForTest(mentioned, uid.toString()));
        assertFalse(PushWorker.isUserMentionedForTest(mentioned, UUID.randomUUID().toString()));
        assertFalse(PushWorker.isUserMentionedForTest(Set.of(), uid.toString()));
    }
}
