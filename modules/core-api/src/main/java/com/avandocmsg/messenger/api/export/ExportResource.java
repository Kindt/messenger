package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportCancelResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

/**
 * Queues a chat export job for {@link com.avandocmsg.messenger.worker.exportreplay.ExportReplayWorker}
 * and exposes job status from {@code export_jobs}.
 */
@Path("/v1/chats/{chatId}/export")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Export", description = "Export / compliance replay job enqueue and status")
public class ExportResource {
    private static final Logger log = LoggerFactory.getLogger(ExportResource.class);

    private final ChatRepository chatRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportJobEnqueuer exportJobEnqueuer;
    private final AuditRepository auditRepository;
    private final UserMessageSource messages;
    private final ExportFileAccess exportFileAccess;
    private final NatsOutboundPort natsOutbound;

    @Inject
    public ExportResource(ChatRepository chatRepository, ExportJobRepository exportJobRepository,
                          ExportJobEnqueuer exportJobEnqueuer, AuditRepository auditRepository,
                          UserMessageSource messages, ExportFileAccess exportFileAccess,
                          NatsOutboundPort natsOutbound) {
        this.chatRepository = chatRepository;
        this.exportJobRepository = exportJobRepository;
        this.exportJobEnqueuer = exportJobEnqueuer;
        this.auditRepository = auditRepository;
        this.messages = messages;
        this.exportFileAccess = exportFileAccess;
        this.natsOutbound = natsOutbound;
    }

    @POST
    @Operation(summary = "Request chat export",
        description = "Ставит в очередь задачу на **msg.export.replay**, создаёт строку **export_jobs** (status **queued**), пишет **audit_events** (**export.requested**). "
            + "Доступ: участник чата с ролью **owner** или **admin**, не **banned**. "
            + "Статус — **GET …/export/{jobId}**; JSON — **GET …/export/{jobId}/download** (нужен общий **EXPORT_DIR** с воркером). "
            + "Ответ **202** с **job_id**.")
    @ApiResponse(responseCode = "202", description = "Job accepted",
        content = @Content(schema = @Schema(implementation = ExportAcceptedResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Chat or membership not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response requestExport(@PathParam("chatId") String chatIdStr,
                                  @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");

        var denied = authorizeExport(chatId, userId);
        if (denied != null) {
            return denied;
        }

        UUID jobId;
        try {
            jobId = exportJobEnqueuer.enqueue(chatId, userId, "api", null);
        } catch (ExportJobEnqueuer.ExportEnqueueException e) {
            log.error("Failed to queue export for chat {}", chatId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.export.queue_failed")))
                .build();
        }

        return Response.status(Response.Status.ACCEPTED)
            .entity(new ExportAcceptedResponse(jobId.toString(), chatIdStr, "accepted"))
            .build();
    }

    @GET
    @Path("/{jobId}")
    @Operation(summary = "Get export job status",
        description = "Возвращает строку **export_jobs** для данного чата. Те же права, что у **POST** (owner/admin). "
            + "Терминальные статусы: **export_v1**, **stub_written**, **export_failed**, **export_cancelled**; "
            + "в процессе: **queued**, **processing**.")
    @ApiResponse(responseCode = "200", description = "Job status",
        content = @Content(schema = @Schema(implementation = ExportJobStatusResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Job or chat not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response getExportStatus(@PathParam("chatId") String chatIdStr,
                                    @PathParam("jobId") String jobIdStr,
                                    @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");

        var denied = authorizeExport(chatId, userId);
        if (denied != null) {
            return denied;
        }

        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.job_not_found")))
                .build();
        }

        return Response.ok(ExportJobReadSupport.toStatusResponse(row.get())).build();
    }

    @DELETE
    @Path("/{jobId}")
    @Operation(summary = "Cancel export job",
        description = "Отменяет задачу в **queued** или **processing**. Публикует **msg.export.replay.cancel**.")
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportCancelResponse.class)))
    @ApiResponse(responseCode = "409", description = "Job not cancellable",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response cancelExport(@PathParam("chatId") String chatIdStr,
                                 @PathParam("jobId") String jobIdStr,
                                 @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");

        var denied = authorizeExport(chatId, userId);
        if (denied != null) {
            return denied;
        }

        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }

        return ExportJobCancelSupport.cancel(
            row.get(),
            chatId,
            jobId,
            userId,
            ExportJobCancelSupport.AUDIT_USER_CANCEL,
            exportJobRepository,
            auditRepository,
            messages,
            natsOutbound);
    }

    @GET
    @Path("/{jobId}/attachments")
    @Operation(summary = "List attachment manifest for zip export",
        description = "Возвращает записи из **attachments/manifest.json** для завершённого ZIP-экспорта "
            + "(без скачивания тел). Пагинация: **offset**, **limit** (0 = все; макс. "
            + ExportJobReadSupport.MAX_ATTACHMENT_PAGE_SIZE + "). "
            + "Для выборочной загрузки: **GET …/download?part=binary|binaries**.")
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportAttachmentsListResponse.class)))
    public Response listAttachments(@PathParam("chatId") String chatIdStr,
                                    @PathParam("jobId") String jobIdStr,
                                    @QueryParam("offset") @DefaultValue("0") int offset,
                                    @QueryParam("limit") @DefaultValue("0") int limit,
                                    @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");

        var denied = authorizeExport(chatId, userId);
        if (denied != null) {
            return denied;
        }

        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.job_not_found")))
                .build();
        }
        return ExportJobReadSupport.attachmentsResponse(
            row.get(), exportFileAccess, messages, offset, limit);
    }

    @GET
    @Path("/{jobId}/download")
    @Produces({MediaType.APPLICATION_JSON, "application/zip"})
    @Operation(summary = "Download export artifact",
        description = "По умолчанию (**part=bundle**) — весь артефакт (JSON или ZIP). "
            + "**part=json** — только **export.json** (из ZIP или одиночный файл). "
            + "**part=manifest** — **attachments/manifest.json** (только ZIP с телами файлов). "
            + "**part=binary** — одно вложение по **file_id** (UUID из manifest; только ZIP). "
            + "**part=binaries** — ZIP с несколькими вложениями (**file_ids**, через запятую, макс. "
            + ExportFileAccess.MAX_BINARIES_FILE_IDS + "). "
            + "Источник: MinIO (**minio:**) или **EXPORT_DIR**.")
    @ApiResponse(responseCode = "200", description = "Export JSON file")
    @ApiResponse(responseCode = "409", description = "Job not finished",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Export directory not configured on API host",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response downloadExport(@PathParam("chatId") String chatIdStr,
                                   @PathParam("jobId") String jobIdStr,
                                   @QueryParam("part") @DefaultValue("bundle") String part,
                                   @QueryParam("file_id") String fileIdStr,
                                   @QueryParam("file_ids") String fileIdsStr,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");

        var denied = authorizeExport(chatId, userId);
        if (denied != null) {
            return denied;
        }

        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.job_not_found")))
                .build();
        }

        return ExportDownloadSupport.download(
            row.get(),
            chatId,
            jobId,
            userId,
            ExportDownloadSupport.AUDIT_USER_DOWNLOAD,
            exportFileAccess,
            auditRepository,
            messages,
            part,
            fileIdStr,
            fileIdsStr);
    }

    private Response authorizeExport(UUID chatId, UUID userId) {
        var role = chatRepository.getMemberRole(chatId, userId);
        if (role == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.chat_not_found_or_member")))
                .build();
        }
        if (chatRepository.isMemberBanned(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.export.banned")))
                .build();
        }
        if (!"owner".equals(role) && !"admin".equals(role)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.export.role_required")))
                .build();
        }
        return null;
    }

}
