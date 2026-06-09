package com.avandocmsg.messenger.api.mls.wire;

import java.util.List;
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
}
