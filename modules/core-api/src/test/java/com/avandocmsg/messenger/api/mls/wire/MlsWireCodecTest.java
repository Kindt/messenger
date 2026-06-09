package com.avandocmsg.messenger.api.mls.wire;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MlsWireCodecTest {

    @Test
    void welcome_roundTrip() {
        var groupId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var m1 = UUID.randomUUID();
        var m2 = UUID.randomUUID();
        var tree = "group-seed".getBytes();
        var original = new MlsWelcomePayload(
            groupId, chatId, 0L,
            "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
            MlsWireCodec.treeHash(tree),
            List.of(m1, m2));

        var decoded = MlsWireCodec.decodeWelcome(MlsWireCodec.encodeWelcome(original));

        assertEquals(original.groupId(), decoded.groupId());
        assertEquals(original.chatId(), decoded.chatId());
        assertEquals(original.epoch(), decoded.epoch());
        assertEquals(original.cipherSuite(), decoded.cipherSuite());
        assertArrayEquals(original.treeHash(), decoded.treeHash());
        assertEquals(original.memberUserIds(), decoded.memberUserIds());
    }

    @Test
    void commit_roundTrip() {
        var groupId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var member = UUID.randomUUID();
        var treeHash = MlsWireCodec.treeHash("epoch-1".getBytes());
        var original = new MlsCommitPayload(groupId, chatId, 2L, MlsCommitPayload.Action.ADD, member, treeHash);

        var decoded = MlsWireCodec.decodeCommit(MlsWireCodec.encodeCommit(original));

        assertEquals(original.groupId(), decoded.groupId());
        assertEquals(original.chatId(), decoded.chatId());
        assertEquals(original.epoch(), decoded.epoch());
        assertEquals(original.action(), decoded.action());
        assertEquals(original.memberUserId(), decoded.memberUserId());
        assertArrayEquals(original.treeHash(), decoded.treeHash());
    }

    @Test
    void epoch_roundTrip() {
        var groupId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var treeHash = MlsWireCodec.treeHash("epoch-2".getBytes());
        var original = new MlsEpochPayload(groupId, chatId, 3L, treeHash);

        var decoded = MlsWireCodec.decodeEpoch(MlsWireCodec.encodeEpoch(original));

        assertEquals(original.groupId(), decoded.groupId());
        assertEquals(original.chatId(), decoded.chatId());
        assertEquals(original.epoch(), decoded.epoch());
        assertArrayEquals(original.treeHash(), decoded.treeHash());
    }

    @Test
    void decodeWelcome_rejectsBadMagic() {
        assertThrows(IllegalArgumentException.class, () -> MlsWireCodec.decodeWelcome(new byte[]{1, 2, 3}));
    }
}
