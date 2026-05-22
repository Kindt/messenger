package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepResponse;
import com.avandocmsg.messenger.api.admin.dto.AdminExportSuggestRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportSuggestResponse;
import com.avandocmsg.messenger.api.admin.dto.AdminSessionResponse;
import com.avandocmsg.messenger.api.admin.dto.CreateOrganizationRequest;
import com.avandocmsg.messenger.api.admin.dto.ChatRetentionPolicyResponse;
import com.avandocmsg.messenger.api.admin.dto.RetentionPolicyResponse;
import com.avandocmsg.messenger.api.admin.dto.SetUserOrganizationRequest;
import com.avandocmsg.messenger.api.admin.dto.UpdateRetentionPolicyRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
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
import com.avandocmsg.messenger.api.export.dto.ExportCancelResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Path("/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Операции для пользователей с realm-ролью admin")
@RolesAllowed("admin")
public class AdminResource {

    private final AppConfig appConfig;
    private final AuditRepository auditRepository;
    private final OrganizationRepository organizationRepository;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final ChatRepository chatRepository;
    private final ChatRetentionPolicyRepository chatRetentionPolicyRepository;
    private final ExportSuggestedHandler exportSuggestedHandler;
    private final AdminExportComplianceSeed exportComplianceSeed;
    private final ExportJobEnqueuer exportJobEnqueuer;
    private final ExportJobRepository exportJobRepository;
    private final ExportFileAccess exportFileAccess;
    private final NatsOutboundPort natsOutbound;
    private final UserMessageSource messages;

    @Inject
    public AdminResource(AppConfig appConfig, AuditRepository auditRepository,
                         OrganizationRepository organizationRepository,
                         RetentionPolicyRepository retentionPolicyRepository,
                         ChatRepository chatRepository,
                         ChatRetentionPolicyRepository chatRetentionPolicyRepository,
                         ExportSuggestedHandler exportSuggestedHandler,
                         AdminExportComplianceSeed exportComplianceSeed,
                         ExportJobEnqueuer exportJobEnqueuer,
                         ExportJobRepository exportJobRepository,
                         ExportFileAccess exportFileAccess,
                         NatsOutboundPort natsOutbound,
                         UserMessageSource messages) {
        this.appConfig = appConfig;
        this.auditRepository = auditRepository;
        this.organizationRepository = organizationRepository;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.chatRepository = chatRepository;
        this.chatRetentionPolicyRepository = chatRetentionPolicyRepository;
        this.exportSuggestedHandler = exportSuggestedHandler;
        this.exportComplianceSeed = exportComplianceSeed;
        this.exportJobEnqueuer = exportJobEnqueuer;
        this.exportJobRepository = exportJobRepository;
        this.exportFileAccess = exportFileAccess;
        this.natsOutbound = natsOutbound;
        this.messages = messages;
    }

    @GET
    @Path("session")
    @Operation(summary = "Текущая сессия (admin)",
        description = "Проверка bearer-токена и realm-ролей; удобно для отладки UI после входа под csadmin/admin.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public AdminSessionResponse session(@Context SecurityContext securityContext) {
        var p = securityContext.getUserPrincipal();
        var realmRoles = new ArrayList<String>();
        if (p instanceof UserPrincipal up) {
            realmRoles.addAll(up.realmRoles());
            return new AdminSessionResponse(
                up.userId(),
                up.username(),
                realmRoles,
                appConfig.version()
            );
        }
        return new AdminSessionResponse("", "", realmRoles, appConfig.version());
    }

    @GET
    @Path("audit-events")
    @Operation(summary = "Последние события аудита",
        description = "Опционально **`action`**, **`resource_type`** и/или **`resource_id`** — точное совпадение с колонками (AND). Ретенция: **`message.retention.hot_body_cleared`**, **`message.retention.bulk_cleared`**. Экспорт: **`export.requested`**, **`export.downloaded`**, **`export.cancelled`**, **`export.admin_downloaded`**, **`export.admin_cancelled`**, **`export.admin_inspected`**, **`export.suggested`**, **`export.auto_queued`**, **`export.auto_queue_skipped`** (в админ-UI — кнопки-пресеты). См. **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Response listAudit(
        @QueryParam("limit") @DefaultValue("100") int limit,
        @QueryParam("action") String action,
        @QueryParam("resource_type") String resourceType,
        @QueryParam("resource_id") String resourceId
    ) {
        var rows = auditRepository.listRecent(limit, action, resourceType, resourceId);
        var out = new ArrayList<AuditEventJson>();
        for (var r : rows) {
            out.add(new AuditEventJson(r.id(), r.occurredAt(), r.actorUserId(), r.action(),
                r.resourceType(), r.resourceId(), r.detailsJson()));
        }
        return Response.ok(out).build();
    }

    @POST
    @Path("export-compliance-prep")
    @Operation(summary = "Подготовить чат для export/retention smokes",
        description = "Создаёт group (опционально), PATCH политики ретенции (hot_body=0, deep_archive) и N тестовых сообщений. "
            + "При **include_file=true** — upload + сообщение type=file. "
            + "Требует **EXPORT_ADMIN_SUGGEST_ENABLED=true** (тот же флаг, что и export-suggest).",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(
            schema = @Schema(implementation = AdminExportCompliancePrepResponse.class),
            examples = @ExampleObject(
                name = "with_file",
                value = """
                    {
                      "chat_id": "11111111-1111-1111-1111-111111111111",
                      "message_ids": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"],
                      "retention_patched": true,
                      "file_id": "22222222-2222-2222-2222-222222222222",
                      "file_message_id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
                    }
                    """
            )
        ))
    @ApiResponse(responseCode = "400", description = "Validation error",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "EXPORT_ADMIN_SUGGEST_ENABLED=false",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response exportCompliancePrep(
        @RequestBody(
            description = "Пустое тело допустимо (по умолчанию: create_group=true, message_count=3)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AdminExportCompliancePrepRequest.class),
                examples = {
                    @ExampleObject(
                        name = "new_group_text",
                        summary = "Новый group, только text",
                        value = "{\"create_group\":true,\"message_count\":3}"
                    ),
                    @ExampleObject(
                        name = "new_group_with_file",
                        summary = "Новый group + file attachment",
                        value = """
                            {
                              "create_group": true,
                              "message_count": 2,
                              "include_file": true,
                              "file_name": "compliance-smoke.txt"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "existing_chat",
                        summary = "Существующий chat_id",
                        value = """
                            {
                              "chat_id": "11111111-1111-1111-1111-111111111111",
                              "create_group": false,
                              "message_count": 3
                            }
                            """
                    )
                }
            )
        )
        AdminExportCompliancePrepRequest body,
        @Context SecurityContext securityContext
    ) {
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

    @POST
    @Path("chats/{chatId}/export-suggest")
    @Operation(summary = "Опубликовать подсказку export (dev/compliance)",
        description = "Эмулирует **msg.export.suggested** без NATS CLI. Требует **EXPORT_ADMIN_SUGGEST_ENABLED=true**. "
            + "**dispatch**: **local** (аудит + auto-queue на этом узле), **nats** (только publish), **both**.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "202", description = "Suggestion accepted")
    public Response exportSuggest(
        @PathParam("chatId") String chatIdStr,
        AdminExportSuggestRequest body
    ) {
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

    @POST
    @Path("chats/{chatId}/export")
    @Operation(summary = "Поставить export-задачу в очередь (admin)",
        description = "Публикует **msg.export.replay** и создаёт **export_jobs** (как пользовательский POST export). "
            + "Требует **EXPORT_ADMIN_EXPORT_ENABLED=true**. **requested_by** — текущий admin.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "202",
        content = @Content(schema = @Schema(implementation = ExportAcceptedResponse.class)))
    public Response requestExport(
        @PathParam("chatId") String chatIdStr,
        @Context SecurityContext securityContext
    ) {
        if (!appConfig.exportAdminExportEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_enqueue_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!chatRepository.chatExists(chatId)) {
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

    @GET
    @Path("chats/{chatId}/export/jobs")
    @Operation(summary = "Список export-задач чата (admin)",
        description = "Последние задачи по **created_at** (новые первыми). Опционально **status** "
            + "(queued, processing, export_v1, stub_written, export_failed, export_cancelled). **limit** по умолчанию 20, макс. "
            + ExportJobReadSupport.MAX_JOBS_LIST_SIZE + ".",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportJobListResponse.class)))
    public Response listExportJobs(
        @PathParam("chatId") String chatIdStr,
        @QueryParam("status") String status,
        @QueryParam("limit") @DefaultValue("20") int limit,
        @Context SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var lim = ExportJobReadSupport.normalizeJobsListLimit(limit);
        var rows = exportJobRepository.listForChat(chatId, status, lim);
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

    @GET
    @Path("export/jobs")
    @Operation(summary = "Список export-задач (все чаты, admin)",
        description = "Последние задачи по **created_at** (новые первыми). Опционально **status**, **chat_id**, **limit** (макс. "
            + ExportJobReadSupport.MAX_JOBS_LIST_SIZE + "). Аудит: **export.admin_inspected** / view **global_jobs_list**.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportAdminJobsListResponse.class)))
    public Response listAllExportJobs(
        @QueryParam("status") String status,
        @QueryParam("chat_id") String chatIdStr,
        @QueryParam("limit") @DefaultValue("30") int limit,
        @Context SecurityContext securityContext
    ) {
        UUID chatFilter = null;
        if (chatIdStr != null && !chatIdStr.isBlank()) {
            chatFilter = UuidParams.required(chatIdStr, "chat_id");
        }
        var lim = ExportJobReadSupport.normalizeJobsListLimit(limit);
        var statusFilter = status != null && !status.isBlank() ? status.trim() : null;
        var rows = exportJobRepository.listRecent(statusFilter, chatFilter, lim);
        var items = rows.stream().map(ExportJobReadSupport::toAdminListItem).toList();
        auditExportGlobalJobsList(securityContext, statusFilter, chatFilter, items.size());
        return Response.ok(new ExportAdminJobsListResponse(
            statusFilter,
            chatFilter != null ? chatFilter.toString() : null,
            items.size(),
            items)).build();
    }

    @GET
    @Path("chats/{chatId}/export/latest/status")
    @Operation(summary = "Статус последней export-задачи чата (admin)",
        description = "Без проверки членства в чате. Для операторской консоли.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportJobStatusResponse.class)))
    public Response exportLatestStatus(
        @PathParam("chatId") String chatIdStr,
        @Context SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var row = exportJobRepository.findLatestForChat(chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, row.get().id(), chatId, "latest_status");
        return Response.ok(ExportJobReadSupport.toStatusResponse(row.get())).build();
    }

    @GET
    @Path("chats/{chatId}/export/{jobId}/status")
    @Operation(summary = "Статус export-задачи (admin)",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportJobStatusResponse.class)))
    public Response exportJobStatus(
        @PathParam("chatId") String chatIdStr,
        @PathParam("jobId") String jobIdStr,
        @Context SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, jobId, chatId, "status");
        return Response.ok(ExportJobReadSupport.toStatusResponse(row.get())).build();
    }

    @DELETE
    @Path("chats/{chatId}/export/{jobId}")
    @Operation(summary = "Отменить export (admin)",
        description = "**queued** или **processing**. Требует **EXPORT_ADMIN_EXPORT_ENABLED=true**. "
            + "Аудит: **export.admin_cancelled**; NATS: **msg.export.replay.cancel**.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportCancelResponse.class)))
    public Response cancelExportJob(
        @PathParam("chatId") String chatIdStr,
        @PathParam("jobId") String jobIdStr,
        @Context SecurityContext securityContext
    ) {
        if (!appConfig.exportAdminExportEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.admin_enqueue_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        return ExportJobCancelSupport.cancel(
            row.get(),
            chatId,
            jobId,
            CurrentUserId.uuid(securityContext),
            ExportJobCancelSupport.AUDIT_ADMIN_CANCEL,
            exportJobRepository,
            auditRepository,
            messages,
            natsOutbound);
    }

    @GET
    @Path("chats/{chatId}/export/{jobId}/attachments")
    @Operation(summary = "Список вложений export ZIP (admin)",
        description = "Тот же JSON, что **GET /v1/chats/{chatId}/export/{jobId}/attachments**; пагинация: **offset**, **limit** (макс. "
            + ExportJobReadSupport.MAX_ATTACHMENT_PAGE_SIZE + ").",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportAttachmentsListResponse.class)))
    public Response exportJobAttachments(
        @PathParam("chatId") String chatIdStr,
        @PathParam("jobId") String jobIdStr,
        @QueryParam("offset") @DefaultValue("0") int offset,
        @QueryParam("limit") @DefaultValue("0") int limit,
        @Context SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
        if (row.isEmpty()) {
            return ExportJobReadSupport.jobNotFound(messages);
        }
        auditExportInspect(securityContext, jobId, chatId, "attachments");
        return ExportJobReadSupport.attachmentsResponse(row.get(), exportFileAccess, messages, offset, limit);
    }

    @GET
    @Path("chats/{chatId}/export/{jobId}/download")
    @Produces({MediaType.APPLICATION_JSON, "application/zip"})
    @Operation(summary = "Скачать артефакт export (admin)",
        description = "Те же **part** / **file_id** / **file_ids**, что у пользовательского download. "
            + "Аудит: **export.admin_downloaded**.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Response exportJobDownload(
        @PathParam("chatId") String chatIdStr,
        @PathParam("jobId") String jobIdStr,
        @QueryParam("part") @DefaultValue("bundle") String part,
        @QueryParam("file_id") String fileIdStr,
        @QueryParam("file_ids") String fileIdsStr,
        @Context SecurityContext securityContext
    ) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var jobId = UuidParams.required(jobIdStr, "job_id");
        var row = exportJobRepository.findByIdAndChat(jobId, chatId);
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
            auditRepository,
            messages,
            part,
            fileIdStr,
            fileIdsStr);
    }

    @GET
    @Path("organizations/{orgId}/retention")
    @Operation(summary = "Политика ретенции организации (эффективные значения)",
        description = "Слияние строки org_retention_policy (V011) с дефолтами из конфигурации. См. docs/RETENTION_AND_DEEP_ARCHIVE.md.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Response getOrganizationRetention(@PathParam("orgId") String orgIdStr) {
        var orgId = UuidParams.required(orgIdStr, "org_id");
        if (!organizationRepository.exists(orgId)) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.org_not_found"))).build();
        }
        var stored = retentionPolicyRepository.findByOrgId(orgId);
        var body = RetentionPolicyResponse.resolved(orgId, appConfig, stored);
        return Response.ok(body).build();
    }

    @GET
    @Path("chats/{chatId}/retention")
    @Operation(summary = "Политика ретенции чата (эффективные значения)",
        description = "Платформа → org (по владельцу/участникам) → chat_retention_policy (V012). См. docs/RETENTION_AND_DEEP_ARCHIVE.md.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Response getChatRetention(@PathParam("chatId") String chatIdStr) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!chatRepository.chatExists(chatId)) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.chat_not_found"))).build();
        }
        var baseOrgId = chatRepository.findOrgIdForRetentionOverlay(chatId);
        var orgStored = baseOrgId.flatMap(retentionPolicyRepository::findByOrgId);
        var chatStored = chatRetentionPolicyRepository.findByChatId(chatId);
        var body = ChatRetentionPolicyResponse.resolved(chatId, baseOrgId, appConfig, orgStored, chatStored);
        return Response.ok(body).build();
    }

    @PATCH
    @Path("chats/{chatId}/retention")
    @Operation(summary = "Обновить политику ретенции чата",
        description = "Upsert в chat_retention_policy; в ответе — эффективные значения. Аудит: chat.retention.set.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UpdateRetentionPolicyRequest.class)))
    @ApiResponse(responseCode = "200", description = "Обновлено",
        content = @Content(schema = @Schema(implementation = ChatRetentionPolicyResponse.class)))
    @ApiResponse(responseCode = "400", description = "Некорректное тело или возраст",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Чат не найден",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Не удалось сохранить",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response patchChatRetention(@PathParam("chatId") String chatIdStr,
                                       UpdateRetentionPolicyRequest request,
                                       @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ApiError(400, messages.get("error.admin.body_required"))).build();
        }
        if (request.archiveMetadataEnabled() == null || request.deepArchiveEnabled() == null || request.legalHold() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.retention_flags_required")))
                .build();
        }
        Integer bodyDays = request.hotMessageBodyMaxAgeDays();
        Integer metaDays = request.hotMetadataMinAgeDays();
        if (bodyDays != null && bodyDays < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.hot_body_nonneg")))
                .build();
        }
        if (metaDays != null && metaDays < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.hot_meta_nonneg")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!chatRepository.chatExists(chatId)) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.chat_not_found"))).build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        var ok = chatRetentionPolicyRepository.upsert(
            chatId,
            bodyDays,
            metaDays,
            request.archiveMetadataEnabled(),
            request.deepArchiveEnabled(),
            request.legalHold(),
            actor
        );
        if (!ok) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ApiError(502, messages.get("error.admin.save_chat_retention_failed")))
                .build();
        }
        var details = retentionPolicyPatchAuditDetails(
            bodyDays,
            metaDays,
            request.archiveMetadataEnabled(),
            request.deepArchiveEnabled(),
            request.legalHold()
        );
        auditRepository.record(actor, "chat.retention.set", "chat", chatIdStr, details);
        var baseOrgId = chatRepository.findOrgIdForRetentionOverlay(chatId);
        var orgStored = baseOrgId.flatMap(retentionPolicyRepository::findByOrgId);
        var chatStored = chatRetentionPolicyRepository.findByChatId(chatId);
        var body = ChatRetentionPolicyResponse.resolved(chatId, baseOrgId, appConfig, orgStored, chatStored);
        return Response.ok(body).build();
    }

    @PATCH
    @Path("organizations/{orgId}/retention")
    @Operation(summary = "Обновить политику ретенции организации",
        description = "Upsert в org_retention_policy; в ответе — эффективные значения после записи. Аудит: organization.retention.set.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UpdateRetentionPolicyRequest.class)))
    @ApiResponse(responseCode = "200", description = "Обновлено",
        content = @Content(schema = @Schema(implementation = RetentionPolicyResponse.class)))
    @ApiResponse(responseCode = "400", description = "Некорректное тело или возраст",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Организация не найдена",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Не удалось сохранить",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response patchOrganizationRetention(@PathParam("orgId") String orgIdStr,
                                               UpdateRetentionPolicyRequest request,
                                               @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ApiError(400, messages.get("error.admin.body_required"))).build();
        }
        if (request.archiveMetadataEnabled() == null || request.deepArchiveEnabled() == null || request.legalHold() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.retention_flags_required")))
                .build();
        }
        Integer bodyDays = request.hotMessageBodyMaxAgeDays();
        Integer metaDays = request.hotMetadataMinAgeDays();
        if (bodyDays != null && bodyDays < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.hot_body_nonneg")))
                .build();
        }
        if (metaDays != null && metaDays < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.admin.hot_meta_nonneg")))
                .build();
        }
        var orgId = UuidParams.required(orgIdStr, "org_id");
        if (!organizationRepository.exists(orgId)) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.org_not_found"))).build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        var ok = retentionPolicyRepository.upsert(
            orgId,
            bodyDays,
            metaDays,
            request.archiveMetadataEnabled(),
            request.deepArchiveEnabled(),
            request.legalHold(),
            actor
        );
        if (!ok) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ApiError(502, messages.get("error.admin.save_retention_failed")))
                .build();
        }
        var details = retentionPolicyPatchAuditDetails(
            bodyDays,
            metaDays,
            request.archiveMetadataEnabled(),
            request.deepArchiveEnabled(),
            request.legalHold()
        );
        auditRepository.record(actor, "organization.retention.set", "organization", orgIdStr, details);
        var stored = retentionPolicyRepository.findByOrgId(orgId);
        var body = RetentionPolicyResponse.resolved(orgId, appConfig, stored);
        return Response.ok(body).build();
    }

    @GET
    @Path("organizations")
    @Operation(summary = "Список организаций (multi-tenant, ТЗ п. 23)", security = @SecurityRequirement(name = "bearerAuth"))
    public Response listOrganizations() {
        var rows = organizationRepository.listAll();
        var out = new ArrayList<OrganizationJson>();
        for (var o : rows) {
            out.add(new OrganizationJson(o.id(), o.name(), o.createdAt()));
        }
        return Response.ok(out).build();
    }

    @POST
    @Path("organizations")
    @Operation(summary = "Создать организацию", security = @SecurityRequirement(name = "bearerAuth"))
    public Response createOrganization(CreateOrganizationRequest request,
                                     @Context SecurityContext securityContext) {
        if (request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ApiError(400, messages.get("error.admin.name_required"))).build();
        }
        var org = organizationRepository.create(request.name());
        if (org == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.admin.org_create_failed")))
                .build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        auditRepository.record(actor, "organization.create", "organization", org.id(),
            organizationCreateAuditDetails(org.name()));
        return Response.status(Response.Status.CREATED)
            .entity(new OrganizationJson(org.id(), org.name(), org.createdAt()))
            .build();
    }

    @DELETE
    @Path("organizations/{orgId}")
    @Operation(summary = "Удалить организацию (если нет пользователей)", security = @SecurityRequirement(name = "bearerAuth"))
    public Response deleteOrganization(@PathParam("orgId") String orgIdStr,
                                       @Context SecurityContext securityContext) {
        var orgId = UuidParams.required(orgIdStr, "org_id");
        var orgRow = organizationRepository.findById(orgId);
        if (orgRow.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.org_not_found"))).build();
        }
        var orgName = orgRow.get().name();
        if (!organizationRepository.deleteIfUnused(orgId)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.admin.org_has_users")))
                .build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        auditRepository.record(actor, "organization.delete", "organization", orgIdStr,
            organizationDeleteAuditDetails(orgName));
        return Response.noContent().build();
    }

    @PATCH
    @Path("users/{userId}/organization")
    @Operation(summary = "Назначить пользователю организацию", security = @SecurityRequirement(name = "bearerAuth"))
    public Response setUserOrganization(@PathParam("userId") String userIdStr,
                                       SetUserOrganizationRequest request,
                                       @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ApiError(400, messages.get("error.admin.body_required"))).build();
        }
        var orgId = UuidParams.required(request.orgId(), "org_id");
        var userId = UuidParams.required(userIdStr, "user_id");
        var ok = organizationRepository.setUserOrg(userId, orgId);
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.user_not_updated"))).build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        auditRepository.record(actor, "user.organization.set", "user", userIdStr,
            userOrganizationSetAuditDetails(orgId));
        return Response.noContent().build();
    }

    private static final ObjectMapper ADMIN_AUDIT_JSON = new ObjectMapper();

    private static String writeAdminAuditJson(ObjectNode n) {
        try {
            return ADMIN_AUDIT_JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Package-visible for tests. */
    static String userOrganizationSetAuditDetails(UUID orgId) {
        var n = ADMIN_AUDIT_JSON.createObjectNode();
        n.put("org_id", orgId.toString());
        return writeAdminAuditJson(n);
    }

    private static String organizationNameAuditJson(String name) {
        var n = ADMIN_AUDIT_JSON.createObjectNode();
        n.put("name", name);
        return writeAdminAuditJson(n);
    }

    /** Persisted display name after create. Package-visible for tests. */
    static String organizationCreateAuditDetails(String name) {
        return organizationNameAuditJson(name);
    }

    /** Name at delete time (same JSON shape as create). Package-visible for tests. */
    static String organizationDeleteAuditDetails(String name) {
        return organizationNameAuditJson(name);
    }

    /** JSON for retention PATCH audit; null ages → JSON null. Package-visible for tests. */
    static String retentionPolicyPatchAuditDetails(
        Integer hotMessageBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold
    ) {
        var n = ADMIN_AUDIT_JSON.createObjectNode();
        if (hotMessageBodyMaxAgeDays == null) {
            n.putNull("hot_message_body_max_age_days");
        } else {
            n.put("hot_message_body_max_age_days", hotMessageBodyMaxAgeDays);
        }
        if (hotMetadataMinAgeDays == null) {
            n.putNull("hot_metadata_min_age_days");
        } else {
            n.put("hot_metadata_min_age_days", hotMetadataMinAgeDays);
        }
        n.put("archive_metadata_enabled", archiveMetadataEnabled);
        n.put("deep_archive_enabled", deepArchiveEnabled);
        n.put("legal_hold", legalHold);
        return writeAdminAuditJson(n);
    }

    private void auditExportInspect(SecurityContext securityContext, UUID jobId, UUID chatId, String view) {
        try {
            var details = ADMIN_AUDIT_JSON.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("view", view);
            auditRepository.record(
                CurrentUserId.uuid(securityContext),
                "export.admin_inspected",
                "export_job",
                jobId.toString(),
                writeAdminAuditJson(details));
        } catch (Exception ignored) {
            // audit must not block operator reads
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
            auditRepository.record(
                CurrentUserId.uuid(securityContext),
                "export.admin_inspected",
                "export_jobs",
                "global",
                writeAdminAuditJson(details));
        } catch (Exception ignored) {
            // audit must not block operator reads
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

    public record AuditEventJson(
        long id,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("actor_user_id") String actorUserId,
        String action,
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") String resourceId,
        @JsonProperty("details_json") String detailsJson
    ) {}

    public record OrganizationJson(
        String id,
        String name,
        @JsonProperty("created_at") Instant createdAt
    ) {}
}
