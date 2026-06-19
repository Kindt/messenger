package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Compliance audit event log ({@code audit_events}). */
public interface AuditPort {
    void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson);

    List<AuditRow> listRecent(int limit);

    List<AuditRow> listRecent(int limit, String actionEquals);

    List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals);

    List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals);

    long countByAction(String action);

    Optional<Instant> latestOccurredAtByAction(String action);

    record AuditRow(long id, Instant occurredAt, String actorUserId, String action,
                    String resourceType, String resourceId, String detailsJson) {}
}
