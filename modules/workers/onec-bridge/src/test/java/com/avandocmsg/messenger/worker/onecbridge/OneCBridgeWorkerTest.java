package com.avandocmsg.messenger.worker.onecbridge;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OneCBridgeWorkerTest {

    @Test
    void pingReturnsPong() {
        var event = new PluginEvent("e1", UUID.randomUUID(), "L2", "mention", null, null, "ping", Map.of(), Map.of());
        var response = OneCBridgeWorker.handle(event);
        assertTrue(response.messages().getFirst().text().contains("pong"));
    }
}
