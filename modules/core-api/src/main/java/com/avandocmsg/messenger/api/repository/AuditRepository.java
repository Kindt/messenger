package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.core.port.AuditPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for audit JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcAuditAdapter}.
 */
public class AuditRepository {
    private final AuditPort port;

    public AuditRepository(DataSource dataSource) {
        this.port = new JdbcAuditAdapter(dataSource);
    }

    AuditRepository(AuditPort port) {
        this.port = port;
    }

    public void recordEvent(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
        port.record(actorUserId, action, resourceType, resourceId, detailsJson);
    }

    public List<AuditRow> listRecent(int limit) {
        return port.listRecent(limit).stream().map(AuditRepository::map).toList();
    }

    public List<AuditRow> listRecent(int limit, String actionEquals) {
        return port.listRecent(limit, actionEquals).stream().map(AuditRepository::map).toList();
    }

    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
        return port.listRecent(limit, actionEquals, resourceTypeEquals).stream().map(AuditRepository::map).toList();
    }

    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals) {
        return port.listRecent(limit, actionEquals, resourceTypeEquals, resourceIdEquals).stream()
            .map(AuditRepository::map).toList();
    }

    public long countByAction(String action) {
        return port.countByAction(action);
    }

    public Optional<Instant> latestOccurredAtByAction(String action) {
        return port.latestOccurredAtByAction(action);
    }

    private static AuditRow map(AuditPort.AuditRow row) {
        return new AuditRow(row.id(), row.occurredAt(), row.actorUserId(), row.action(),
            row.resourceType(), row.resourceId(), row.detailsJson());
    }

    public record AuditRow(long id, Instant occurredAt, String actorUserId, String action,
                           String resourceType, String resourceId, String detailsJson) {}
}
