package com.avandocmsg.messenger.worker.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelineReadCacheInvalidatorTest {

    @Test
    void invalidateAfterMessageSend_deletesListAndUnreadKeys() {
        var redis = mock(io.lettuce.core.api.sync.RedisCommands.class);
        var invalidator = PipelineReadCacheInvalidator.forTest(redis);
        var sender = UUID.randomUUID();
        var member = UUID.randomUUID();
        invalidator.invalidateAfterMessageSend(List.of(member.toString()), sender);
        verify(redis).del(
            "korus:rc:chat:list:" + sender,
            "korus:rc:chat:unread:" + sender);
        verify(redis).del(
            "korus:rc:chat:list:" + member,
            "korus:rc:chat:unread:" + member);
    }

    @Test
    void disabledInvalidator_isNoOp() {
        var invalidator = PipelineReadCacheInvalidator.disabled();
        invalidator.invalidateAfterMessageSend(List.of(UUID.randomUUID().toString()), UUID.randomUUID());
        org.junit.jupiter.api.Assertions.assertFalse(invalidator.enabled());
    }
}
