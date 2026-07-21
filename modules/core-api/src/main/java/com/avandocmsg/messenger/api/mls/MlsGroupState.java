package com.avandocmsg.messenger.api.mls;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Row in {@code mls_group_state} (RFC 9420 scaffold — tree blob opaque at this layer). */
public record MlsGroupState(
    UUID groupId,
    UUID chatId,
    long epoch,
    byte[] treeData,
    Instant createdAt,
    Instant updatedAt
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MlsGroupState that)) {
            return false;
        }
        return epoch == that.epoch
            && Objects.equals(groupId, that.groupId)
            && Objects.equals(chatId, that.chatId)
            && Arrays.equals(treeData, that.treeData)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, chatId, epoch, Arrays.hashCode(treeData), createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "MlsGroupState[groupId=" + groupId
            + ", chatId=" + chatId
            + ", epoch=" + epoch
            + ", treeData=" + Arrays.toString(treeData)
            + ", createdAt=" + createdAt
            + ", updatedAt=" + updatedAt + "]";
    }
}
