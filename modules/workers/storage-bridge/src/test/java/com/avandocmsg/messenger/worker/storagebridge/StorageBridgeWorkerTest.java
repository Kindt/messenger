package com.avandocmsg.messenger.worker.storagebridge;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBridgeWorkerTest {

    @Test
    void pingReturnsPong() {
        var event = new PluginEvent("e1", UUID.randomUUID(), "L2", "mention", null, null, "ping", Map.of(), Map.of());
        var response = StorageBridgeWorker.handle(event);
        assertTrue(response.messages().getFirst().text().contains("pong"));
    }
}
