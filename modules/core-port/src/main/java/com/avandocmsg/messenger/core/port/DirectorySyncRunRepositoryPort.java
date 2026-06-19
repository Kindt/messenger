package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Directory sync run persistence (hex Phase 2c). */
public interface DirectorySyncRunRepositoryPort {

    record DirectorySyncRunRow(
        UUID id,
        UUID orgId,
        String status,
        int usersUpserted,
        String error,
        Instant startedAt,
        Instant finishedAt
    ) {}

    DirectorySyncRunRow startRun(UUID orgId);

    void finishRun(UUID runId, String status, int usersUpserted, String error);

    Optional<DirectorySyncRunRow> findLatestByOrg(UUID orgId);
}
