package com.avandocmsg.messenger.api.mls;

import java.time.Instant;
import java.util.UUID;

/** Row in {@code mls_group_state} (RFC 9420 scaffold — tree blob opaque at this layer). */
public record MlsGroupState(
    UUID groupId,
    UUID chatId,
    long epoch,
    byte[] treeData,
    Instant createdAt,
    Instant updatedAt
) {}
