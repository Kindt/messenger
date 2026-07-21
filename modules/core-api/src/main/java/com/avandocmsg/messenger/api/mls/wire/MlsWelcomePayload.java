package com.avandocmsg.messenger.api.mls.wire;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Decoded MLS Welcome wire payload (RFC 9420 phase-1 structured bytes, not full OpenMLS). */
public record MlsWelcomePayload(
    UUID groupId,
    UUID chatId,
    long epoch,
    String cipherSuite,
    byte[] treeHash,
    List<UUID> memberUserIds
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MlsWelcomePayload that)) {
            return false;
        }
        return epoch == that.epoch
            && Objects.equals(groupId, that.groupId)
            && Objects.equals(chatId, that.chatId)
            && Objects.equals(cipherSuite, that.cipherSuite)
            && Arrays.equals(treeHash, that.treeHash)
            && Objects.equals(memberUserIds, that.memberUserIds);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(groupId, chatId, epoch, cipherSuite, memberUserIds);
        result = 31 * result + Arrays.hashCode(treeHash);
        return result;
    }

    @Override
    public String toString() {
        return "MlsWelcomePayload[groupId=" + groupId
            + ", chatId=" + chatId
            + ", epoch=" + epoch
            + ", cipherSuite=" + cipherSuite
            + ", treeHash=" + Arrays.toString(treeHash)
            + ", memberUserIds=" + memberUserIds
            + "]";
    }
}
