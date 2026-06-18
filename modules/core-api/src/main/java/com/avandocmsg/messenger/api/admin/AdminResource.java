package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.admin.dto.LegalHoldResponse;
import com.avandocmsg.messenger.api.admin.dto.LegalHoldUpdateRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepResponse;
import com.avandocmsg.messenger.api.admin.dto.AdminExportSuggestRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminSessionResponse;
import com.avandocmsg.messenger.api.admin.dto.CreateOrganizationRequest;
import com.avandocmsg.messenger.api.admin.dto.ChatRetentionPolicyResponse;
import com.avandocmsg.messenger.api.admin.dto.RetentionPolicyResponse;
import com.avandocmsg.messenger.api.admin.dto.SetUserOrganizationRequest;
import com.avandocmsg.messenger.api.admin.dto.UpdateRetentionPolicyRequest;
import com.avandocmsg.messenger.api.chats.ReadReceiptService;
import com.avandocmsg.messenger.api.mls.MlsGroupManager;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.core.adapter.mls.NoOpOpenMlsBindingAdapter;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportJobReadSupport;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAdminJobsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportCancelResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.api.repository.LegalHoldRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Операции для пользователей с realm-ролью admin")
@RolesAllowed("admin")
public class AdminResource {

    private final AppConfig appConfig;
    private final AuditRepository auditRepository;
    private final OrganizationApplicationService organizationApplicationService;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final ChatRepository chatRepository;
    private final ChatRetentionPolicyRepository chatRetentionPolicyRepository;
    private final AdminExportFacade exportFacade;
    private final ReadReceiptService readReceiptService;
    private final MlsGroupManager mlsGroupManager;
    private final MlsMigrationService mlsMigrationService;
    private final OpenMlsBindingPort openMlsBindingPort;
    private final LegalHoldRepository legalHoldRepository;
    private final PurgeStatusService purgeStatusService;
    private final UserMessageSource messages;

    @Inject
    public AdminResource(AppConfig appConfig, AuditRepository auditRepository,
                         OrganizationApplicationService organizationApplicationService,
                         RetentionPolicyRepository retentionPolicyRepository,
                         ChatRepository chatRepository,
                         ChatRetentionPolicyRepository chatRetentionPolicyRepository,
                         ExportSuggestedHandler exportSuggestedHandler,
                         AdminExportComplianceSeed exportComplianceSeed,
                         ExportJobEnqueuer exportJobEnqueuer,
                         ExportJobRepository exportJobRepository,
                         ExportFileAccess exportFileAccess,
                         NatsOutboundPort natsOutbound,
                         ReadReceiptService readReceiptService,
                         MlsGroupManager mlsGroupManager,
                         MlsMigrationService mlsMigrationService,
                         OpenMlsBindingPort openMlsBindingPort,
                         LegalHoldRepository legalHoldRepository,
                         PurgeStatusService purgeStatusService,
                         UserMessageSource messages) {
        this.appConfig = appConfig;
        this.auditRepository = auditRepository;
        this.organizationApplicationService = organizationApplicationService;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.chatRepository = chatRepository;
        this.chatRetentionPolicyRepository = chatRetentionPolicyRepository;
        this.exportFacade = new AdminExportFacade(
            appConfig,
            auditRepository,
            chatRepository,
            exportSuggestedHandler,
            exportComplianceSeed,
            exportJobEnqueuer,
            exportJobRepository,
            exportFileAccess,
            natsOutbound,
            messages
        );
        this.readReceiptService = readReceiptService;
        this.mlsGroupManager = mlsGroupManager;
        this.mlsMigrationService = mlsMigrationService;
        this.openMlsBindingPort = openMlsBindingPort;
        this.legalHoldRepository = legalHoldRepository;
        this.purgeStatusService = purgeStatusService;
        this.messages = messages;
    }

    @GET
    @Path("purge/status")
    @Operation(summary = "Hot-row purge status (audit-derived)")
    public Response purgeStatus() {
        if (purgeStatusService == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        return Response.ok(purgeStatusService.status()).build();
    }

    @GET
    @Path("legal-hold/organizations/{orgId}")
    @Operation(summary = "Extended legal-hold flags for organization")
    public Response getOrgLegalHold(@PathParam("orgId") String orgIdStr) {
        var orgId = UuidParams.required(orgIdStr, "org_id");
        if (legalHoldRepository == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        var row = legalHoldRepository.findOrg(orgId)
            .orElse(new LegalHoldRepository.LegalHoldRow(false, false, false));
        return Response.ok(toLegalHoldResponse(row)).build();
    }

    @PATCH
    @Path("legal-hold/organizations/{orgId}")
    @Operation(summary = "Update extended legal-hold flags for organization")
    public Response patchOrgLegalHold(@PathParam("orgId") String orgIdStr,
                                      LegalHoldUpdateRequest request,
                                      @Context SecurityContext securityContext) {
        return patchLegalHold(true, orgIdStr, request, securityContext);
    }

    @GET
    @Path("legal-hold/chats/{chatId}")
    @Operation(summary = "Extended legal-hold flags for chat")
    public Response getChatLegalHold(@PathParam("chatId") String chatIdStr) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (legalHoldRepository == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        var row = legalHoldRepository.findChat(chatId)
            .orElse(new LegalHoldRepository.LegalHoldRow(false, false, false));
        return Response.ok(toLegalHoldResponse(row)).build();
    }

    @PATCH
    @Path("legal-hold/chats/{chatId}")
    @Operation(summary = "Update extended legal-hold flags for chat")
    public Response patchChatLegalHold(@PathParam("chatId") String chatIdStr,
                                       LegalHoldUpdateRequest request,
                                       @Context SecurityContext securityContext) {
        return patchLegalHold(false, chatIdStr, request, securityContext);
    }

    private Response patchLegalHold(boolean org, String idStr, LegalHoldUpdateRequest request,
                                    SecurityContext securityContext) {
        if (legalHoldRepository == null || request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var id = UuidParams.required(idStr, org ? "org_id" : "chat_id");
        var actor = CurrentUserId.uuid(securityContext);
        var current = org
            ? legalHoldRepository.findOrg(id).orElse(new LegalHoldRepository.LegalHoldRow(false, false, false))
            : legalHoldRepository.findChat(id).orElse(new LegalHoldRepository.LegalHoldRow(false, false, false));
        var next = new LegalHoldRepository.LegalHoldRow(
            request.legalHold() != null ? request.legalHold() : current.legalHold(),
            request.legalHoldFiles() != null ? request.legalHoldFiles() : current.legalHoldFiles(),
            request.legalHoldDeepArchive() != null ? request.legalHoldDeepArchive() : current.legalHoldDeepArchive());
        var ok = org
            ? legalHoldRepository.upsertOrg(id, next, actor)
            : legalHoldRepository.upsertChat(id, next, actor);
        if (!ok) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ApiError(502, messages.get("error.admin.save_retention_failed")))
                .build();
        }
        auditRepository.record(actor, org ? "organization.legal_hold.set" : "chat.legal_hold.set",
            org ? "organization" : "chat", id.toString(), null);
        return Response.ok(toLegalHoldResponse(next)).build();
    }

    private static LegalHoldResponse toLegalHoldResponse(LegalHoldRepository.LegalHoldRow row) {
        return new LegalHoldResponse(row.legalHold(), row.legalHoldFiles(), row.legalHoldDeepArchive());
    }

    @POST
    @Path("e2ee/migrate-openmls-batch")
    @Operation(summary = "Batch migrate legacy E2EE chats to OpenMLS wire profile")
    public Response migrateOpenMlsBatch(@QueryParam("limit") @DefaultValue("50") int limit) {
        if (mlsMigrationService == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.message.send_failed")))
                .build();
        }
        var result = mlsMigrationService.batchMigrateToOpenMls(limit);
        return Response.ok(new BatchMigrationResponse(
            result.migratedCount(),
            result.failedCount(),
            result.remainingPending(),
            result.migratedChatIds(),
            result.failedChatIds())).build();
    }

    @POST
    @Path("e2ee/migrate-batch")
    @Operation(summary = "Batch migrate legacy E2EE chats to MLS")
    public Response migrateBatch(@QueryParam("limit") @DefaultValue("50") int limit) {
        if (mlsMigrationService == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.message.send_failed")))
                .build();
        }
        var result = mlsMigrationService.batchMigrateToMls(limit);
        return Response.ok(new BatchMigrationResponse(
            result.migratedCount(),
            result.failedCount(),
            result.remainingPending(),
            result.migratedChatIds(),
            result.failedChatIds())).build();
    }

    public record BatchMigrationResponse(
        @JsonProperty("migrated_count") int migratedCount,
        @JsonProperty("failed_count") int failedCount,
        @JsonProperty("remaining_pending") long remainingPending,
        @JsonProperty("migrated_chat_ids") List<UUID> migratedChatIds,
        @JsonProperty("failed_chat_ids") List<UUID> failedChatIds
    ) {
    }

    @GET
    @Path("e2ee/status")
    @Operation(summary = "E2EE/MLS status")
    public Response e2eeStatus() {
        var groups = mlsGroupManager != null ? mlsGroupManager.groupCount() : 0L;
        var pending = mlsMigrationService != null ? mlsMigrationService.pendingMigrationCount() : 0L;
        var binding = openMlsBindingPort != null ? openMlsBindingPort : NoOpOpenMlsBindingAdapter.INSTANCE;
        return Response.ok(new E2eeStatusResponse(
            groups,
            pending,
            appConfig.mlsStatus(),
            appConfig.e2eeSchemes(),
            binding.wireProfile(),
            binding.libraryVersion(),
            binding.nativeBindingAvailable())).build();
    }

    public record E2eeStatusResponse(
        @JsonProperty("mls_group_count") long mlsGroupCount,
        @JsonProperty("pending_migrations_count") long pendingMigrationsCount,
        @JsonProperty("mls_status") String mlsStatus,
        @JsonProperty("e2ee_schemes") List<String> e2eeSchemes,
        @JsonProperty("openmls_wire_profile") String openmlsWireProfile,
        @JsonProperty("openmls_library_version") String openmlsLibraryVersion,
        @JsonProperty("openmls_native_binding") boolean openmlsNativeBinding
    ) {
    }

    @GET
    @Path("read-receipts/stats")
    @Operation(summary = "Read receipt statistics")
    public Response readReceiptStats() {
        var total = readReceiptService.totalRows();
        com.avandocmsg.messenger.api.metrics.ReadReceiptMetrics.setRepositorySize(total);
        return Response.ok(new ReadReceiptStatsResponse(total)).build();
    }

    public record ReadReceiptStatsResponse(@JsonProperty("total_rows") long totalRows) {
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
        return exportFacade.exportCompliancePrep(body, securityContext);
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
        return exportFacade.exportSuggest(chatIdStr, body);
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
        return exportFacade.requestExport(chatIdStr, securityContext);
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
        return exportFacade.listExportJobs(chatIdStr, status, limit, securityContext);
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
        return exportFacade.listAllExportJobs(status, chatIdStr, limit, securityContext);
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
        return exportFacade.exportLatestStatus(chatIdStr, securityContext);
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
        return exportFacade.exportJobStatus(chatIdStr, jobIdStr, securityContext);
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
        return exportFacade.cancelExportJob(chatIdStr, jobIdStr, securityContext);
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
        return exportFacade.exportJobAttachments(chatIdStr, jobIdStr, offset, limit, securityContext);
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
        return exportFacade.exportJobDownload(
            chatIdStr,
            jobIdStr,
            part,
            fileIdStr,
            fileIdsStr,
            securityContext
        );
    }

    @GET
    @Path("organizations/{orgId}/retention")
    @Operation(summary = "Политика ретенции организации (эффективные значения)",
        description = "Слияние строки org_retention_policy (V011) с дефолтами из конфигурации. См. docs/RETENTION_AND_DEEP_ARCHIVE.md.",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Response getOrganizationRetention(@PathParam("orgId") String orgIdStr) {
        var orgId = UuidParams.required(orgIdStr, "org_id");
        if (!organizationApplicationService.exists(OrganizationId.of(orgId))
            || organizationApplicationService.findById(OrganizationId.of(orgId)).isEmpty()) {
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
        if (!organizationApplicationService.exists(OrganizationId.of(orgId))) {
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
        var rows = organizationApplicationService.listAll();
        var out = new ArrayList<OrganizationJson>();
        for (var o : rows) {
            out.add(new OrganizationJson(o.id().value().toString(), o.name(), o.createdAt()));
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
        var org = organizationApplicationService.create(request.name());
        if (org.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.admin.org_create_failed")))
                .build();
        }
        var created = org.get();
        var actor = CurrentUserId.uuid(securityContext);
        auditRepository.record(actor, "organization.create", "organization", created.id().value().toString(),
            organizationCreateAuditDetails(created.name()));
        return Response.status(Response.Status.CREATED)
            .entity(new OrganizationJson(created.id().value().toString(), created.name(), created.createdAt()))
            .build();
    }

    @DELETE
    @Path("organizations/{orgId}")
    @Operation(summary = "Удалить организацию (если нет пользователей)", security = @SecurityRequirement(name = "bearerAuth"))
    public Response deleteOrganization(@PathParam("orgId") String orgIdStr,
                                       @Context SecurityContext securityContext) {
        var orgId = UuidParams.required(orgIdStr, "org_id");
        var orgRow = organizationApplicationService.findById(OrganizationId.of(orgId));
        if (orgRow.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ApiError(404, messages.get("error.admin.org_not_found"))).build();
        }
        var orgName = orgRow.get().name();
        if (!organizationApplicationService.deleteIfUnused(OrganizationId.of(orgId))) {
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
        var ok = organizationApplicationService.setUserOrg(UserId.of(userId), OrganizationId.of(orgId));
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
