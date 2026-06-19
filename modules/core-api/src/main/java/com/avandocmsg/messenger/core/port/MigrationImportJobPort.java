package com.avandocmsg.messenger.core.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Admin migration import job queue. */
public interface MigrationImportJobPort {
    UUID insert(UUID orgId, String source, String configJson, UUID createdBy);

    Optional<JobRow> findById(UUID id);

    List<JobRow> listForOrg(UUID orgId, int limit);

    boolean updateStatus(UUID id, String status, String resultJson);

    record JobRow(
        UUID id,
        UUID orgId,
        String source,
        String status,
        String configJson,
        String resultJson,
        UUID createdBy
    ) {}
}
