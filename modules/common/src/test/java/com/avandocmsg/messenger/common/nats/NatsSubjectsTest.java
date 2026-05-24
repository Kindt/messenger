package com.avandocmsg.messenger.common.nats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for NATS subject strings (pipeline, gateway, workers must stay aligned).
 */
class NatsSubjectsTest {

    @Test
    void subjects_areStableForInterop() {
        assertEquals("msg.send", NatsSubjects.MSG_SEND);
        assertEquals("msg.deliver.", NatsSubjects.MSG_DELIVER_PREFIX);
        assertEquals("msg.event.index", NatsSubjects.MSG_EVENT_INDEX);
        assertEquals("msg.event.push", NatsSubjects.MSG_EVENT_PUSH);
        assertEquals("msg.event.bot", NatsSubjects.MSG_EVENT_BOT);
        assertEquals("msg.event.deep-archive", NatsSubjects.MSG_EVENT_DEEP_ARCHIVE);
        assertEquals("msg.event.retention", NatsSubjects.MSG_EVENT_RETENTION);
        assertEquals("msg.export.replay", NatsSubjects.MSG_EXPORT_REPLAY);
        assertEquals("msg.export.replay.complete", NatsSubjects.MSG_EXPORT_REPLAY_COMPLETE);
        assertEquals("msg.export.suggested", NatsSubjects.MSG_EXPORT_SUGGESTED);
        assertEquals("msg.export.replay.cancel", NatsSubjects.MSG_EXPORT_REPLAY_CANCEL);
        assertEquals("msg.read_receipt", NatsSubjects.MSG_READ_RECEIPT);
        assertEquals("$SVC.heartbeat.", NatsSubjects.SVC_HEARTBEAT_PREFIX);
        assertEquals("$SVC.heartbeat.*", NatsSubjects.SVC_HEARTBEAT_WILDCARD);
        assertEquals("$SVC.lifecycle.", NatsSubjects.SVC_LIFECYCLE_PREFIX);
    }
}
