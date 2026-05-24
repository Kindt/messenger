package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPurgeNatsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void indexDeleteEvent_usesDeleteOpAndSubject() throws Exception {
        var msgId = "11111111-1111-1111-1111-111111111111";
        var evt = MessageWorkerEvent.forIndexDelete(msgId);
        var json = MAPPER.writeValueAsString(evt);
        assertTrue(json.contains("delete"));
        assertEquals("msg.event.index", NatsSubjects.MSG_EVENT_INDEX);
    }
}
