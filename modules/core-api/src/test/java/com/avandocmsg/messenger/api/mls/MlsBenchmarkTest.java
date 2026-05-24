package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lightweight timing guard for MLS stub encrypt path (not JMH). */
class MlsBenchmarkTest {

    @Test
    void encrypt100Messages_underGenerousBudget() {
        var chatId = UUID.randomUUID();
        var sessionRepository = new MlsGroupManagerTest.StubSessionRepository();
        var mlsService = new MlsService(sessionRepository, new E2EEService());
        var groupRepo = new MlsGroupManagerTest.InMemoryGroupStateRepository();
        var clock = Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC);
        var manager = new MlsGroupManager(groupRepo, mlsService, UuidGenerator.standard(), clock);
        var groupId = manager.createGroup(chatId, List.of(UUID.randomUUID(), UUID.randomUUID()));
        var senderId = UUID.randomUUID();

        var start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            var enc = manager.encrypt(groupId, senderId, "msg-" + i);
            assertTrue(enc != null && enc.ciphertextBase64() != null && !enc.ciphertextBase64().isBlank());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        var avgMs = elapsedMs / 100.0;
        assertTrue(avgMs < 200.0, "avg encrypt ms=" + avgMs);
    }
}
