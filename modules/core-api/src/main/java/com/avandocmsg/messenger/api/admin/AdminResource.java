package com.avandocmsg.messenger.api.admin;

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
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.DELETE;
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
    private final UserMessageSource messages;

    @Inject
    public AdminResource(AppConfig appConfig, AuditRepository auditRepository,
                         OrganizationRepository organizationRepository,
                         RetentionPolicyRepository retentionPolicyRepository,
                         ChatRepository chatRepository,
                         ChatRetentionPolicyRepository chatRetentionPolicyRepository,
                         UserMessageSource messages) {
        this.appConfig = appConfig;
        this.auditRepository = auditRepository;
        this.organizationRepository = organizationRepository;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.chatRepository = chatRepository;
        this.chatRetentionPolicyRepository = chatRetentionPolicyRepository;
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
        description = "Опционально **`action`**, **`resource_type`** и/или **`resource_id`** — точное совпадение с колонками (AND). Примеры ретенции: **`action=message.retention.hot_body_cleared`**, **`resource_type=message`**; сводка прохода: **`action=message.retention.bulk_cleared`**, **`resource_type=retention_pass`**; по UUID прохода: **`resource_id=<pass_id>`** (тот же, что в **`msg.event.retention.pass_id`**). В **`details_json`** построчной ретенции и в сводке может быть поле **`pass_id`** — для корреляции с NATS без разбора только **`resource_id`** (у построчной строки **`resource_id`** — id сообщения). См. **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8.",
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
