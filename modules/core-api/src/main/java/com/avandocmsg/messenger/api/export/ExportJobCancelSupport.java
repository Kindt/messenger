package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.export.dto.ExportCancelResponse;
import com.avandocmsg.messenger.api.metrics.ExportMetrics;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;

import java.util.Set;
import java.util.UUID;

/** Cancel queued or processing export jobs (user and admin APIs). */
public final class ExportJobCancelSupport {

    public static final String STATUS_CANCELLED = "export_cancelled";
    public static final String AUDIT_USER_CANCEL = "export.cancelled";
    public static final String AUDIT_ADMIN_CANCEL = "export.admin_cancelled";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> CANCELLABLE = Set.of("queued", "processing");

    private ExportJobCancelSupport() {}

    public static Response cancel(
        ExportJobRepository.ExportJobRow job,
        UUID chatId,
        UUID jobId,
        UUID actorUserId,
        String auditAction,
        ExportJobRepository exportJobRepository,
        AuditRepository auditRepository,
        UserMessageSource messages,
        NatsOutboundPort natsOutbound
    ) {
        if (!CANCELLABLE.contains(job.status())) {
            ExportMetrics.jobCancelRejected(auditAction, "not_cancellable");
            return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.export.not_cancellable")))
                .build();
        }
        if (!exportJobRepository.cancelIfActive(jobId, chatId)) {
            ExportMetrics.jobCancelRejected(auditAction, "db_race");
            return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.export.not_cancellable")))
                .build();
        }
        ExportCancelPublisher.publish(natsOutbound, jobId, chatId);
        ExportMetrics.jobCancelled(auditAction, job.status());
        auditRepository.record(
            actorUserId,
            auditAction,
            "export_job",
            jobId.toString(),
            auditDetails(chatId, job.status()));
        return Response.ok(new ExportCancelResponse(
            jobId.toString(),
            chatId.toString(),
            STATUS_CANCELLED,
            true)).build();
    }

    private static String auditDetails(UUID chatId, String previousStatus) {
        try {
            return MAPPER.writeValueAsString(MAPPER.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("previous_status", previousStatus));
        } catch (Exception e) {
            return "{\"chat_id\":\"" + chatId + "\"}";
        }
    }
}
