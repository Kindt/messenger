package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationImportProcessorTest {

    @Test
    void process_skipsAlreadyCompletedJob() {
        var jobId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var statusHolder = new String[] { "completed" };
        var repo = new MigrationImportJobRepository(null) {
            @Override
            public Optional<JobRow> findById(UUID id) {
                if (!id.equals(jobId)) {
                    return Optional.empty();
                }
                return Optional.of(
                    new JobRow(jobId, orgId, "telegram_export_v1", statusHolder[0], "{}", null, UUID.randomUUID())
                );
            }

            @Override
            public boolean updateStatus(UUID id, String newStatus, String resultJson) {
                statusHolder[0] = newStatus;
                return true;
            }
        };
        var processor = new MigrationImportProcessor(repo);
        var out = processor.process(jobId);
        assertTrue(out.isPresent());
        assertEquals("completed", out.get().status());
        assertEquals("completed", statusHolder[0]);
    }

    @Test
    void process_withoutDataSourceMarksFailed() {
        var jobId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var statusHolder = new String[] { "pending" };
        var repo = new MigrationImportJobRepository(null) {
            @Override
            public Optional<JobRow> findById(UUID id) {
                if (!id.equals(jobId)) {
                    return Optional.empty();
                }
                return Optional.of(
                    new JobRow(jobId, orgId, "telegram_export_v1", statusHolder[0], "{}", null, UUID.randomUUID())
                );
            }

            @Override
            public boolean updateStatus(UUID id, String newStatus, String resultJson) {
                if (id.equals(jobId)) {
                    statusHolder[0] = newStatus;
                    return true;
                }
                return false;
            }
        };
        var processor = new MigrationImportProcessor(repo);
        var out = processor.process(jobId);
        assertTrue(out.isPresent());
        assertEquals("failed", out.get().status());
    }
}
