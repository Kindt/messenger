package com.avandocmsg.messenger.worker.connectorruntime;

import com.avandocmsg.messenger.common.plugin.PluginEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorRuntimeWorkerTest {

    @Test
    void pingReturnsPong() {
        var event = new PluginEvent(
            "e1",
            UUID.randomUUID(),
            "L1",
            "mention",
            null,
            null,
            "ping",
            Map.of(),
            Map.of()
        );
        var response = ConnectorRuntimeWorker.handle(event);
        assertTrue(response.messages().getFirst().text().contains("pong"));
    }

    @Test
    void jiraProfileWithoutKeyShowsHelp() {
        var event = new PluginEvent(
            "e2",
            UUID.randomUUID(),
            "L1",
            "slash",
            null,
            null,
            "/jira",
            Map.of(),
            Map.of("preset_id", "jira-connector")
        );
        var response = ConnectorRuntimeWorker.handle(event);
        assertTrue(response.messages().getFirst().text().contains("Jira"));
    }
}
