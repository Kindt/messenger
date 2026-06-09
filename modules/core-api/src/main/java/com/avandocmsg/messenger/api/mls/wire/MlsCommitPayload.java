package com.avandocmsg.messenger.api.mls.wire;

import java.util.UUID;

/** Decoded MLS Commit wire payload (RFC 9420 phase-1 structured bytes, not full OpenMLS). */
public record MlsCommitPayload(
    UUID groupId,
    UUID chatId,
    long epoch,
    Action action,
    UUID memberUserId,
    byte[] treeHash
) {
    public enum Action {
        ADD((byte) 0),
        REMOVE((byte) 1);

        private final byte wireValue;

        Action(byte wireValue) {
            this.wireValue = wireValue;
        }

        public byte wireValue() {
            return wireValue;
        }

        public static Action fromWire(byte value) {
            for (var a : values()) {
                if (a.wireValue == value) {
                    return a;
                }
            }
            throw new IllegalArgumentException("Unknown commit action: " + value);
        }
    }
}
