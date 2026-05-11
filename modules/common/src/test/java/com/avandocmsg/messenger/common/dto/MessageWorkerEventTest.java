package com.avandocmsg.messenger.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MessageWorkerEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromSendEvent_marksEncryptedWhenE2eePrefix() {
        var send = new MessageSendEvent(
            "mid", "cid", "sid", "e2ee-text", "blob", "cmid", 1000L);
        var ev = MessageWorkerEvent.fromSendEvent(send);
        assertTrue(ev.encrypted());
        assertEquals("e2ee-text", ev.type());
        assertEquals("blob".getBytes(StandardCharsets.UTF_8).length, ev.storageByteLength());
        assertNull(ev.searchText());
        assertNull(ev.indexOp());
    }

    @Test
    void fromSendEvent_plainTextNotEncrypted() {
        var send = new MessageSendEvent(
            "mid", "cid", "sid", "text", "hello", null, null);
        var ev = MessageWorkerEvent.fromSendEvent(send);
        assertFalse(ev.encrypted());
        assertEquals(5, ev.storageByteLength());
        assertEquals("hello", ev.searchText());
        assertNull(ev.indexOp());
    }

    @Test
    void fromPersistedMessage_update_roundTripsIndexOpInJson() throws Exception {
        var ev = MessageWorkerEvent.fromPersistedMessage(
            "mid", "cid", "sid", null, 1L, "text", "hello", "update");
        assertEquals("update", ev.indexOp());
        var json = MAPPER.writeValueAsString(ev);
        assertTrue(json.contains("\"index_op\":\"update\""));
        var back = MAPPER.readValue(json, MessageWorkerEvent.class);
        assertEquals("update", back.indexOp());
        assertEquals("hello", back.searchText());
    }

    @Test
    void forIndexDelete_minimalPayload() throws Exception {
        var ev = MessageWorkerEvent.forIndexDelete("mid");
        assertEquals("delete", ev.indexOp());
        assertEquals("mid", ev.messageId());
        var json = MAPPER.writeValueAsString(ev);
        var back = MAPPER.readValue(json, MessageWorkerEvent.class);
        assertEquals("delete", back.indexOp());
    }
}
