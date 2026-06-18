package com.avandocmsg.messenger.api.directory;

import java.time.Instant;
import java.util.UUID;

public record DirectorySyncRunRow(
    UUID id,
    UUID orgId,
    String status,
    int usersUpserted,
    String error,
    Instant startedAt,
    Instant finishedAt
) {}
