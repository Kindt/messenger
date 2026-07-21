package com.avandocmsg.messenger.api.mls.wire;

import java.util.Arrays;
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
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MlsCommitPayload other)) {
            return false;
        }
        return epoch == other.epoch
            && java.util.Objects.equals(groupId, other.groupId)
            && java.util.Objects.equals(chatId, other.chatId)
            && action == other.action
            && java.util.Objects.equals(memberUserId, other.memberUserId)
            && Arrays.equals(treeHash, other.treeHash);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(groupId, chatId, epoch, action, memberUserId);
        result = 31 * result + Arrays.hashCode(treeHash);
        return result;
    }

    @Override
    public String toString() {
        return "MlsCommitPayload[groupId=" + groupId
            + ", chatId=" + chatId
            + ", epoch=" + epoch
            + ", action=" + action
            + ", memberUserId=" + memberUserId
            + ", treeHash=" + Arrays.toString(treeHash)
            + "]";
    }

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
