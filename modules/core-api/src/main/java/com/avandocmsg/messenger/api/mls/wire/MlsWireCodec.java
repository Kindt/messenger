package com.avandocmsg.messenger.api.mls.wire;

import org.bouncycastle.crypto.digests.SHA256Digest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Incremental RFC 9420 wire codec using Bouncy Castle digests and structured bytes.
 * Not a full OpenMLS binding — payloads are self-describing KMLS envelopes for interop testing.
 */
public final class MlsWireCodec {

    private static final byte[] MAGIC = "KMLS".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 0x01;
    private static final byte TYPE_WELCOME = 0x01;
    private static final byte TYPE_COMMIT = 0x02;
    private static final byte TYPE_EPOCH = 0x03;
    private static final int TREE_HASH_LEN = 32;

    private MlsWireCodec() {
    }

    public static byte[] encodeWelcome(MlsWelcomePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload required");
        }
        var suite = payload.cipherSuite() != null ? payload.cipherSuite() : "";
        var suiteBytes = suite.getBytes(StandardCharsets.UTF_8);
        if (suiteBytes.length > 65535) {
            throw new IllegalArgumentException("cipher suite too long");
        }
        var members = payload.memberUserIds() != null ? payload.memberUserIds() : List.<UUID>of();
        var treeHash = normalizeTreeHash(payload.treeHash());
        var size = 6 + 32 + 32 + 8 + 2 + suiteBytes.length + TREE_HASH_LEN + 4 + members.size() * 16;
        var buf = ByteBuffer.allocate(size);
        writeHeader(buf, TYPE_WELCOME);
        writeUuid(buf, payload.groupId());
        writeUuid(buf, payload.chatId());
        buf.putLong(payload.epoch());
        buf.putShort((short) suiteBytes.length);
        buf.put(suiteBytes);
        buf.put(treeHash);
        buf.putInt(members.size());
        for (var member : members) {
            writeUuid(buf, member);
        }
        return buf.array();
    }

    public static byte[] encodeCommit(MlsCommitPayload payload) {
        if (payload == null || payload.action() == null) {
            throw new IllegalArgumentException("payload and action required");
        }
        var treeHash = normalizeTreeHash(payload.treeHash());
        var buf = ByteBuffer.allocate(6 + 32 + 32 + 8 + 1 + 16 + TREE_HASH_LEN);
        writeHeader(buf, TYPE_COMMIT);
        writeUuid(buf, payload.groupId());
        writeUuid(buf, payload.chatId());
        buf.putLong(payload.epoch());
        buf.put(payload.action().wireValue());
        writeUuid(buf, payload.memberUserId());
        buf.put(treeHash);
        return buf.array();
    }

    public static byte[] encodeEpoch(MlsEpochPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload required");
        }
        var treeHash = normalizeTreeHash(payload.treeHash());
        var buf = ByteBuffer.allocate(6 + 32 + 32 + 8 + TREE_HASH_LEN);
        writeHeader(buf, TYPE_EPOCH);
        writeUuid(buf, payload.groupId());
        writeUuid(buf, payload.chatId());
        buf.putLong(payload.epoch());
        buf.put(treeHash);
        return buf.array();
    }

    public static MlsWelcomePayload decodeWelcome(byte[] wire) {
        var buf = wrapAndValidate(wire, TYPE_WELCOME);
        var groupId = readUuid(buf);
        var chatId = readUuid(buf);
        var epoch = buf.getLong();
        var suiteLen = buf.getShort() & 0xFFFF;
        var suiteBytes = new byte[suiteLen];
        buf.get(suiteBytes);
        var suite = new String(suiteBytes, StandardCharsets.UTF_8);
        var treeHash = readTreeHash(buf);
        var memberCount = buf.getInt();
        if (memberCount < 0) {
            throw new IllegalArgumentException("invalid member count");
        }
        var members = new ArrayList<UUID>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(readUuid(buf));
        }
        return new MlsWelcomePayload(groupId, chatId, epoch, suite, treeHash, List.copyOf(members));
    }

    public static MlsCommitPayload decodeCommit(byte[] wire) {
        var buf = wrapAndValidate(wire, TYPE_COMMIT);
        var groupId = readUuid(buf);
        var chatId = readUuid(buf);
        var epoch = buf.getLong();
        var action = MlsCommitPayload.Action.fromWire(buf.get());
        var memberUserId = readUuid(buf);
        var treeHash = readTreeHash(buf);
        return new MlsCommitPayload(groupId, chatId, epoch, action, memberUserId, treeHash);
    }

    public static MlsEpochPayload decodeEpoch(byte[] wire) {
        var buf = wrapAndValidate(wire, TYPE_EPOCH);
        var groupId = readUuid(buf);
        var chatId = readUuid(buf);
        var epoch = buf.getLong();
        var treeHash = readTreeHash(buf);
        return new MlsEpochPayload(groupId, chatId, epoch, treeHash);
    }

    public static byte[] treeHash(byte[] treeData) {
        var digest = new SHA256Digest();
        var input = treeData != null ? treeData : new byte[0];
        digest.update(input, 0, input.length);
        var out = new byte[TREE_HASH_LEN];
        digest.doFinal(out, 0);
        return out;
    }

    private static void writeHeader(ByteBuffer buf, byte type) {
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(type);
    }

    private static ByteBuffer wrapAndValidate(byte[] wire, byte expectedType) {
        if (wire == null || wire.length < 6) {
            throw new IllegalArgumentException("wire too short");
        }
        var buf = ByteBuffer.wrap(wire);
        var magic = new byte[4];
        buf.get(magic);
        if (!java.util.Arrays.equals(MAGIC, magic)) {
            throw new IllegalArgumentException("invalid KMLS magic");
        }
        var version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported KMLS version: " + version);
        }
        var type = buf.get();
        if (type != expectedType) {
            throw new IllegalArgumentException("unexpected message type: " + type);
        }
        return buf;
    }

    private static void writeUuid(ByteBuffer buf, UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("uuid required");
        }
        buf.putLong(id.getMostSignificantBits());
        buf.putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer buf) {
        return new UUID(buf.getLong(), buf.getLong());
    }

    private static byte[] readTreeHash(ByteBuffer buf) {
        var hash = new byte[TREE_HASH_LEN];
        buf.get(hash);
        return hash;
    }

    private static byte[] normalizeTreeHash(byte[] treeHash) {
        if (treeHash == null) {
            return new byte[TREE_HASH_LEN];
        }
        if (treeHash.length != TREE_HASH_LEN) {
            throw new IllegalArgumentException("tree hash must be 32 bytes");
        }
        return treeHash;
    }
}
