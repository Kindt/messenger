package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.wire.MlsCommitPayload;
import com.avandocmsg.messenger.api.mls.wire.MlsWireCodec;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MlsWireHandlerTest {

    private MlsGroupManagerTest.InMemoryGroupStateRepository groupStateRepository;
    private MlsService mlsService;
    private MlsWireHandler handler;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        groupStateRepository = new MlsGroupManagerTest.InMemoryGroupStateRepository();
        var sessionRepository = new MlsGroupManagerTest.StubSessionRepository();
        mlsService = new MlsService(sessionRepository, new E2EEService());
        var clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        handler = new MlsWireHandler(groupStateRepository, mlsService, clock);
    }

    @Test
    void applyWelcome_createsGroupState() {
        var groupId = UUID.randomUUID();
        var members = List.of(UUID.randomUUID());
        var welcome = new com.avandocmsg.messenger.api.mls.wire.MlsWelcomePayload(
            groupId, chatId, 0L, "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
            MlsWireCodec.treeHash("welcome-tree".getBytes()), members);
        handler.applyWelcome(welcome);
        assertTrue(groupStateRepository.findByGroupId(groupId).isPresent());
        assertEquals(chatId, groupStateRepository.findByGroupId(groupId).orElseThrow().chatId());
    }

    @Test
    void handleCommit_appliesRemoteEpoch() {
        var member = UUID.randomUUID();
        var manager = new MlsGroupManager(groupStateRepository, mlsService, UuidGenerator.standard(),
            Clock.systemUTC(), new MlsGroupManagerTest.RecordingWirePublisher());
        var groupId = manager.createGroup(chatId, List.of(member));
        var commit = new com.avandocmsg.messenger.api.mls.wire.MlsCommitPayload(
            groupId, chatId, 1L, MlsCommitPayload.Action.ADD, UUID.randomUUID(),
            MlsWireCodec.treeHash("tree".getBytes()));
        var wire = MlsWireCodec.encodeCommit(commit);
        handler.handle(NatsSubjects.MLS_COMMIT, wire);
        assertEquals(1L, groupStateRepository.findByGroupId(groupId).orElseThrow().epoch());
    }
}
