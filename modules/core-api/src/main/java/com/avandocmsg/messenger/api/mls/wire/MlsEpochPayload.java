package com.avandocmsg.messenger.api.mls.wire;

import java.util.Arrays;
import java.util.UUID;

/** Decoded MLS epoch notification (post-commit epoch bump). */
public record MlsEpochPayload(
    UUID groupId,
    UUID chatId,
    long epoch,
    byte[] treeHash
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MlsEpochPayload other)) {
            return false;
        }
        return epoch == other.epoch
            && java.util.Objects.equals(groupId, other.groupId)
            && java.util.Objects.equals(chatId, other.chatId)
            && Arrays.equals(treeHash, other.treeHash);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(groupId, chatId, epoch);
        result = 31 * result + Arrays.hashCode(treeHash);
        return result;
    }

    @Override
    public String toString() {
        return "MlsEpochPayload[groupId=" + groupId
            + ", chatId=" + chatId
            + ", epoch=" + epoch
            + ", treeHash=" + Arrays.toString(treeHash)
            + "]";
    }
}
