package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.core.port.AuditPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeniedAccessAuditTest {

    @Test
    void fileAccessDenied_recordsMetricAndAudit() {
        var sink = new RecordingAuditPort();
        var audit = new DeniedAccessAudit(sink);
        var actor = UUID.randomUUID();
        var fileId = UUID.randomUUID();

        audit.fileAccessDenied(actor, fileId);

        assertEquals(1, sink.records.size());
        assertEquals("access.file.denied", sink.records.get(0).action());
        assertEquals(fileId.toString(), sink.records.get(0).resourceId());
    }

    @Test
    void ipAllowlistDenied_recordsOrganizationAudit() {
        var sink = new RecordingAuditPort();
        var audit = new DeniedAccessAudit(sink);
        var orgId = UUID.randomUUID();

        audit.ipAllowlistDenied(UUID.randomUUID(), orgId, "203.0.113.5");

        assertEquals("access.ip_allowlist.denied", sink.records.get(0).action());
        assertEquals("organization", sink.records.get(0).resourceType());
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRow> records = new ArrayList<>();

        @Override
        public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
            records.add(new AuditRow(0, null, actorUserId != null ? actorUserId.toString() : null,
                action, resourceType, resourceId, detailsJson));
        }

        @Override
        public List<AuditRow> listRecent(int limit) {
            return List.copyOf(records);
        }

        @Override
        public List<AuditRow> listRecent(int limit, String actionEquals) {
            return listRecent(limit);
        }

        @Override
        public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
            return listRecent(limit);
        }

        @Override
        public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals) {
            return listRecent(limit);
        }

        @Override
        public long countByAction(String action) {
            return records.stream().filter(r -> action.equals(r.action())).count();
        }

        @Override
        public Optional<java.time.Instant> latestOccurredAtByAction(String action) {
            return Optional.empty();
        }
    }
}
