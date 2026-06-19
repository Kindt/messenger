package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.export.dto.ExportAdminJobsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.ws.rs.core.Response;


/** Shared export job status / manifest reads for user and admin APIs. */
public final class ExportJobReadSupport {

    public static final int MAX_ATTACHMENT_PAGE_SIZE = 500;
    public static final int MAX_JOBS_LIST_SIZE = 100;

    private ExportJobReadSupport() {}

    public static ExportJobListResponse.ExportJobListItem toListItem(ExportJobPort.ExportJobRow j) {
        return new ExportJobListResponse.ExportJobListItem(
            j.id().toString(),
            j.status(),
            j.outputPath(),
            ExportOutputRef.outputStorage(j.outputPath()),
            ExportOutputRef.outputFormat(j.outputPath()),
            j.requestedBy().toString(),
            formatInstant(j.createdAt()),
            formatInstant(j.completedAt()));
    }

    public static ExportAdminJobsListResponse.ExportAdminJobListItem toAdminListItem(
        ExportJobPort.ExportJobRow j
    ) {
        return new ExportAdminJobsListResponse.ExportAdminJobListItem(
            j.id().toString(),
            j.chatId().toString(),
            j.status(),
            j.outputPath(),
            ExportOutputRef.outputStorage(j.outputPath()),
            ExportOutputRef.outputFormat(j.outputPath()),
            j.requestedBy().toString(),
            formatInstant(j.createdAt()),
            formatInstant(j.completedAt()));
    }

    public static ExportJobStatusResponse toStatusResponse(ExportJobPort.ExportJobRow j) {
        return new ExportJobStatusResponse(
            j.id().toString(),
            j.chatId().toString(),
            j.status(),
            j.outputPath(),
            ExportOutputRef.outputStorage(j.outputPath()),
            ExportOutputRef.outputFormat(j.outputPath()),
            j.messageTtlFilterApplied(),
            j.requestedBy().toString(),
            formatInstant(j.createdAt()),
            formatInstant(j.updatedAt()),
            formatInstant(j.completedAt()));
    }

    public static Response attachmentsResponse(
        ExportJobPort.ExportJobRow job,
        ExportFileAccess exportFileAccess,
        UserMessageSource messages,
        int offset,
        int limit
    ) {
        if (!exportFileAccess.isDownloadableStatus(job.status())) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.export.not_ready")))
                .build();
        }
        if (!exportFileAccess.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.export.download_unavailable")))
                .build();
        }
        try {
            return Response.ok(exportFileAccess.listAttachmentManifest(job, offset, limit)).build();
        } catch (java.io.IOException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.file_not_found")))
                .build();
        }
    }

    public static Response jobNotFound(UserMessageSource messages) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ApiError(404, messages.get("error.export.job_not_found")))
            .build();
    }

    public static int normalizeOffset(int offset) {
        return Math.max(0, offset);
    }

    public static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_ATTACHMENT_PAGE_SIZE);
    }

    public static int normalizeJobsListLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, MAX_JOBS_LIST_SIZE);
    }

    private static String formatInstant(java.time.Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
