package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;

import java.util.Optional;

/** Scaffold batch processor for migration import jobs (spec 022 T02273). */
public class MigrationImportProcessor {
    private final MigrationImportJobRepository jobRepository;

    public MigrationImportProcessor(MigrationImportJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Optional<MigrationImportJobRepository.JobRow> process(java.util.UUID jobId) {
        var job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        if (!"pending".equals(job.status()) && !"failed".equals(job.status())) {
            return Optional.of(job);
        }
        jobRepository.updateStatus(jobId, "running", "{\"phase\":\"running\"}");
        var result = """
            {"imported_messages":0,"imported_users":0,"note":"scaffold processor — no Telegram parse yet"}
            """;
        jobRepository.updateStatus(jobId, "completed", result.trim());
        return jobRepository.findById(jobId);
    }
}
