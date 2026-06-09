package com.avandocmsg.messenger.api.mls.wire;

import java.util.UUID;

/** Decoded MLS epoch notification (post-commit epoch bump). */
public record MlsEpochPayload(
    UUID groupId,
    UUID chatId,
    long epoch,
    byte[] treeHash
) {
}
