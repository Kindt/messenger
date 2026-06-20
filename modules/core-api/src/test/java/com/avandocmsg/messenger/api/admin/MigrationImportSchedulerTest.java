package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationImportSchedulerTest {

    @Test
    void tickProcessesPendingJobs() {
        var jobId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var status = new AtomicReference<>("pending");
        MigrationImportJobPort port = new MigrationImportJobPort() {
            @Override
            public UUID insert(UUID orgId, String source, String configJson, UUID createdBy) {
                return null;
            }

            @Override
            public Optional<JobRow> findById(UUID id) {
                if (!id.equals(jobId)) {
                    return Optional.empty();
                }
                return Optional.of(new JobRow(jobId, orgId, "telegram_export_v1", status.get(), "{}", null, UUID.randomUUID()));
            }

            @Override
            public List<JobRow> listForOrg(UUID orgId, int limit) {
                return List.of();
            }

            @Override
            public List<JobRow> listPending(int limit) {
                return List.of(new JobRow(jobId, orgId, "telegram_export_v1", status.get(), "{}", null, UUID.randomUUID()));
            }

            @Override
            public boolean updateStatus(UUID id, String newStatus, String resultJson) {
                if (id.equals(jobId)) {
                    status.set(newStatus);
                    return true;
                }
                return false;
            }
        };
        var cfg = new AppConfig() {
            @Override
            public long migrationImportPollSeconds() {
                return 0;
            }

            @Override
            public int migrationImportBatchSize() {
                return 5;
            }
        };
        var scheduler = new MigrationImportScheduler(cfg, port, null, null);
        scheduler.tick();
        assertEquals("failed", status.get());
    }
}
