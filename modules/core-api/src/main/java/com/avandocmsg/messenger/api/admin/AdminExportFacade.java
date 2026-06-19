package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportSuggestRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportSuggestResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportDownloadSupport;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportJobCancelSupport;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportJobReadSupport;
import com.avandocmsg.messenger.api.export.ExportSuggestDispatch;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAdminJobsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobListResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class AdminExportFacade {
    private static final Logger log = LoggerFactory.getLogger(AdminExportFacade.class);
    private static final ObjectMapper ADMIN_AUDIT_JSON = new ObjectMapper();

    private final AppConfig appConfig;
    private final AuditPort auditPort;
    private final ChatPersistencePort chatPersistencePort;
    private final ExportSuggestedHandler exportSuggestedHandler;
    private final AdminExportComplianceSeed exportComplianceSeed;
    private final ExportJobEnqueuer exportJobEnqueuer;
    private final ExportJobPort exportJobPort;
    private final ExportFileAccess exportFileAccess;
    private final NatsOutboundPort natsOutbound;
    private final UserMessageSource messages;

    AdminExportFacade(
        AppConfig appConfig,
        AuditPort auditPort,
        ChatPersistencePort chatPersistencePort,
        ExportSuggestedHandler exportSuggestedHandler,
        AdminExportComplianceSeed exportComplianceSeed,
        ExportJobEnqueuer exportJobEnqueuer,
        ExportJobPort exportJobPort,
        ExportFileAccess exportFileAccess,
        NatsOutboundPort natsOutbound,
        UserMessageSource messages
    ) {
        this.appConfig = appConfig;
        this.auditPort = auditPort;
        this.chatPersistencePort = chatPersistencePort;
        this.exportSuggestedHandler = exportSuggestedHandler;
        this.exportComplianceSeed = exportComplianceSeed;
        this.exportJobEnqueuer = exportJobEnqueuer;
        this.exportJobPort = exportJobPort;
        this.exportFileAccess = exportFileAccess;
        this.natsOutbound = natsOutbound;
        this.messages = messages;
    }

    Response exportCompliancePrep(AdminExportCompliancePrepRequest body, SecurityContext securityContext) {
        if (!appConfig.exportAdminSuggestEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_suggest_disabled")))
                .build();
        }
        try {
            var result = exportComplianceSeed.prepare(CurrentUserId.uuid(securityContext), body);
            return Response.ok(result.response()).build();
        } catch (IllegalArgumentException e) {
            var detail = compliancePrepErrorMessage(e.getMessage());
            if (detail != null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, detail))
                    .build();
            }
            throw e;
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.export.compliance_prep_failed")))
                .build();
        }
    }

    Response exportSuggest(String chatIdStr, AdminExportSuggestRequest body) {
        if (!appConfig.exportAdminSuggestEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_suggest_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        final ExportSuggestDispatch dispatch;
        try {
            dispatch = ExportSuggestDispatch.parse(body != null ? body.dispatch() : null);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.export.invalid_suggest_dispatch")))
                .build();
        }
        var reason = body != null && body.reason() != null && !body.reason().isBlank()
            ? body.reason().trim()
            : ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES;
        var candidates = body != null && body.candidateMessageCount() != null
            ? Math.max(0, body.candidateMessageCount())
            : 0;
        var suggestedAt = Instant.now().toEpochMilli();
        var event = new ExportSuggestedEvent(chatId.toString(), reason, candidates, suggestedAt);

        if (dispatch == ExportSuggestDispatch.NATS || dispatch == ExportSuggestDispatch.BOTH) {
            try {
                natsOutbound.publish(NatsSubjects.MSG_EXPORT_SUGGESTED, ADMIN_AUDIT_JSON.writeValueAsBytes(event));
                natsOutbound.flush(Duration.ofSeconds(2));
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(new ApiError(502, messages.get("error.export.suggest_nats_failed")))
                    .build();
            }
        }
        java.util.Optional<UUID> autoJob = java.util.Optional.empty();
        if (dispatch == ExportSuggestDispatch.LOCAL || dispatch == ExportSuggestDispatch.BOTH) {
            try {
                autoJob = exportSuggestedHandler.handle(event);
            } catch (JsonProcessingException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiError(500, messages.get("error.export.suggest_handler_failed")))
                    .build();
            }
        }
        return Response.accepted(new AdminExportSuggestResponse(
            chatId.toString(),
            dispatch.name().toLowerCase(),
            reason,
            candidates,
            suggestedAt,
            autoJob.map(UUID::toString).orElse(null)
        )).build();
    }

    Response requestExport(String chatIdStr, SecurityContext securityContext) {
        if (!appConfig.exportAdminExportEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_enqueue_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!chatPersistencePort.chatExists(chatId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.chat_not_found")))
                .build();
        }
        var actorId = CurrentUserId.uuid(securityContext);
        UUID jobId;
        try {
            jobId = exportJobEnqueuer.enqueue(chatId, actorId, "admin_api", null);
        } catch (ExportJobEnqueuer.ExportEnqueueException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.export.queue_failed")))
                .build();
        }
        return Response.status(Response.Status.ACCEPTED)
            .entity(new ExportAcceptedResponse(jobId.toString(), chatId.toString(), "accepted"))
            .build();
    }

    Response listExportJobs(String chatIdStr, String status, int limit, SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var lim = ExportJobReadSupport.normalizeJobsListLimit(limit);
        var rows = exportJobPort.listForChat(chatId, status, lim);
        var items = rows.stream().map(ExportJobReadSupport::toListItem).toList();
        if (!rows.isEmpty()) {
            auditExportInspect(securityContext, rows.getFirst().id(), chatId, "jobs_list");
        }
        var filter = status != null && !status.isBlank() ? status.trim() : null;
        return Response.ok(new ExportJobListResponse(
            chatId.toString(),
            filter,
            items.size(),
            items)).build();
    }

    Response listAllExportJobs(String status, String chatIdStr, int limit, SecurityContext securityContext) {
        UUID chatFilter = null;
        if (chatIdStr != null && !chatIdStr.isBlank()) {
            chatFilter = UuidParams.required(chatIdStr, "chat_id");
        }
        var lim = ExportJobReadSupport.normalizeJobsListLimit(limit);
        var statusFilter = status != null && !status.isBlank() ? status.trim() : null;
        var rows = exportJobPort.listRecent(statusFilter, chatFilter, lim);
        var items = rows.stream().map(ExportJobReadSupport::toAdminListItem).toList();
        auditExportGlobalJobsList(securityContext, statusFilter, chatFilter, items.size());
        return Response.ok(new ExportAdminJobsListResponse(
            statusFilter,
            chatFilter != null ? chatFilter.toString() : null,
            items.size(),
            items)).build();
    }

    Response exportLatestStatus(String chatIdStr, SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var row = exportJobPort.findLatestForChat(chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, row.get().id(), chatId, "latest_status");
        return Response.ok(ExportJobReadSupport.toStatusResponse(row.get())).build();
    }

    Response exportJobStatus(String chatIdStr, String jobIdStr, SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobPort.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, jobId, chatId, "status");
        return Response.ok(ExportJobReadSupport.toStatusResponse(row.get())).build();
    }

    Response cancelExportJob(String chatIdStr, String jobIdStr, SecurityContext securityContext) {
        if (!appConfig.exportAdminExportEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_enqueue_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobPort.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        return ExportJobCancelSupport.cancel(
            row.get(),
            chatId,
            jobId,
            CurrentUserId.uuid(securityContext),
            ExportJobCancelSupport.AUDIT_ADMIN_CANCEL,
            exportJobPort,
            auditPort,
            messages,
            natsOutbound);
    }

    Response exportJobAttachments(
        String chatIdStr,
        String jobIdStr,
        int offset,
        int limit,
        SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobPort.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, jobId, chatId, "attachments");
        return ExportJobReadSupport.attachmentsResponse(row.get(), exportFileAccess, messages, offset, limit);
    }

    Response exportJobDownload(
        String chatIdStr,
        String jobIdStr,
        String part,
        String fileIdStr,
        String fileIdsStr,
        SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobPort.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        return ExportDownloadSupport.download(
            row.get(),
            chatId,
            jobId,
            CurrentUserId.uuid(securityContext),
            ExportDownloadSupport.AUDIT_ADMIN_DOWNLOAD,
            exportFileAccess,
            auditPort,
            messages,
            part,
            fileIdStr,
            fileIdsStr);
    }

    private void auditExportInspect(SecurityContext securityContext, UUID jobId, UUID chatId, String view) {
        try {
            var details = ADMIN_AUDIT_JSON.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("view", view);
            auditPort.record(
                CurrentUserId.uuid(securityContext),
                "export.admin_inspected",
                "export_job",
                jobId.toString(),
                writeAdminAuditJson(details));
        } catch (Exception e) {
            log.warn("export admin inspect audit failed for job {}: {}", jobId, e.getMessage());
        }
    }

    private void auditExportGlobalJobsList(
        SecurityContext securityContext,
        String statusFilter,
        UUID chatIdFilter,
        int resultCount
    ) {
        try {
            var details = ADMIN_AUDIT_JSON.createObjectNode()
                .put("view", "global_jobs_list")
                .put("result_count", resultCount);
            if (statusFilter != null) {
                details.put("status_filter", statusFilter);
            }
            if (chatIdFilter != null) {
                details.put("chat_id_filter", chatIdFilter.toString());
            }
            auditPort.record(
                CurrentUserId.uuid(securityContext),
                "export.admin_inspected",
                "export_jobs",
                "global",
                writeAdminAuditJson(details));
        } catch (Exception e) {
            log.warn("export global jobs list audit failed: {}", e.getMessage());
        }
    }

    private String compliancePrepErrorMessage(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "body_required" -> messages.get("error.admin.body_required");
            case "message_count_range" -> messages.get("error.admin.message_count_range");
            case "chat_id_or_create_group" -> messages.get("error.admin.chat_id_or_create_group");
            case "chat_not_found" -> messages.get("error.admin.chat_not_found");
            case "invalid_chat_id" -> messages.get("error.admin.invalid_chat_id");
            default -> null;
        };
    }

    private static String writeAdminAuditJson(com.fasterxml.jackson.databind.node.ObjectNode n) {
        try {
            return ADMIN_AUDIT_JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
