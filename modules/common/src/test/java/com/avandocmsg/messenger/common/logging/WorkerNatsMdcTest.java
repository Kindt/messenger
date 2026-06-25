package com.avandocmsg.messenger.common.logging;

import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerNatsMdcTest {

    @AfterEach
    void tearDown() {
        WorkerMdcSupport.clear();
    }

    @Test
    void applyFromMessage_readsNatsHeaders() {
        var headers = new Headers();
        headers.add(WorkerMdcSupport.USER_ID, "user-1");
        headers.add(WorkerMdcSupport.X_REQUEST_ID, "req-abc");
        var msg = NatsMessage.builder()
            .subject("msg.send")
            .data(new byte[] {1})
            .headers(headers)
            .build();

        WorkerNatsMdc.applyFromMessage(msg);

        assertEquals("user-1", MDC.get(WorkerMdcSupport.USER_ID));
        assertEquals("req-abc", MDC.get(WorkerMdcSupport.X_REQUEST_ID));
    }
}
