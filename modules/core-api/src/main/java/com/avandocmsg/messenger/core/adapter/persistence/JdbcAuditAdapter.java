package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.core.port.AuditPort;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAuditAdapter implements AuditPort {
    private final AuditRepository delegate;

    public JdbcAuditAdapter(AuditRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcAuditAdapter(DataSource dataSource) {
        this.delegate = new AuditRepository(dataSource);
    }

    @Override
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
        delegate.record(actorUserId, action, resourceType, resourceId, detailsJson);
    }

    @Override
    public List<AuditRow> listRecent(int limit) {
        return delegate.listRecent(limit).stream().map(JdbcAuditAdapter::map).toList();
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals) {
        return delegate.listRecent(limit, actionEquals).stream().map(JdbcAuditAdapter::map).toList();
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
        return delegate.listRecent(limit, actionEquals, resourceTypeEquals).stream().map(JdbcAuditAdapter::map).toList();
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals) {
        return delegate.listRecent(limit, actionEquals, resourceTypeEquals, resourceIdEquals).stream()
            .map(JdbcAuditAdapter::map).toList();
    }

    @Override
    public long countByAction(String action) {
        return delegate.countByAction(action);
    }

    @Override
    public Optional<Instant> latestOccurredAtByAction(String action) {
        return delegate.latestOccurredAtByAction(action);
    }

    private static AuditRow map(AuditRepository.AuditRow row) {
        return new AuditRow(row.id(), row.occurredAt(), row.actorUserId(), row.action(),
            row.resourceType(), row.resourceId(), row.detailsJson());
    }
}
