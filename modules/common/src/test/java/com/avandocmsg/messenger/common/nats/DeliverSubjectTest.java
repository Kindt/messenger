package com.avandocmsg.messenger.common.nats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeliverSubjectTest {

    @Test
    void parse_userSubject() {
        var userId = "550e8400-e29b-41d4-a716-446655440000";
        var target = DeliverSubject.parse(NatsSubjects.deliverUserSubject(userId));
        assertEquals(DeliverSubject.Type.USER, target.type());
        assertEquals(userId, target.id());
    }

    @Test
    void parse_chatSubject() {
        var chatId = "660e8400-e29b-41d4-a716-446655440001";
        var target = DeliverSubject.parse(NatsSubjects.deliverChatSubject(chatId));
        assertEquals(DeliverSubject.Type.CHAT, target.type());
        assertEquals(chatId, target.id());
    }

    @Test
    void parse_rejectsMalformed() {
        assertNull(DeliverSubject.parse("msg.other.foo"));
        assertNull(DeliverSubject.parse(NatsSubjects.MSG_DELIVER_PREFIX));
        assertNull(DeliverSubject.parse(NatsSubjects.MSG_DELIVER_CHAT_PREFIX));
    }
}
