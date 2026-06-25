package com.avandocmsg.messenger.common.nats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JetStreamMessagingSetupTest {

    @Test
    void defaultMaxMessagesPerSubjectIs100k() {
        assertEquals(100_000, JetStreamMessagingSetup.DEFAULT_MAX_MESSAGES_PER_SUBJECT);
    }

    @Test
    void defaultMaxDeliverIs5() {
        assertEquals(5, JetStreamMessagingSetup.DEFAULT_MAX_DELIVER);
    }
}
