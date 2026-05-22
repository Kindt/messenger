package com.avandocmsg.messenger.api.files;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.dto.CreatePublicLinkRequest;
import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.api.files.dto.FileMessageRefResponse;
import com.avandocmsg.messenger.api.files.dto.OwnerPublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.PublicLinkCreatedResponse;
import com.avandocmsg.messenger.api.files.dto.PublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.FileUploadResponse;
import com.avandocmsg.messenger.api.metrics.ApiDeniedMetrics;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.FilePublicLinkRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import jakarta.ws.rs.core.SecurityContext;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Path("/v1/files")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Files", description = "File upload and download")
public class FileResource {

    private static final ObjectMapper FILE_AUDIT_JSON = new ObjectMapper();

    private static String writeFileAuditJson(ObjectNode n) {
        try {
            return FILE_AUDIT_JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Package-visible for tests. */
    static String publicLinkCreateAuditDetails(String linkId, char kind) {
        var n = FILE_AUDIT_JSON.createObjectNode();
        n.put("link_id", linkId);
        n.put("kind", String.valueOf(kind));
        return writeFileAuditJson(n);
    }

    /** Package-visible for tests. */
    static String publicLinkRevokeAuditDetails(UUID linkId) {
        var n = FILE_AUDIT_JSON.createObjectNode();
        n.put("link_id", linkId.toString());
        return writeFileAuditJson(n);
    }

    private final FileService fileService;
    private final AppConfig appConfig;
    private final FilePublicLinkRepository filePublicLinkRepository;
    private final AuditRepository auditRepository;
    private final Clock clock;
    private final UserMessageSource messages;

    @Inject
    public FileResource(FileService fileService, AppConfig appConfig,
                          FilePublicLinkRepository filePublicLinkRepository,
                          AuditRepository auditRepository, Clock clock, UserMessageSource messages) {
        this.fileService = fileService;
        this.appConfig = appConfig;
        this.filePublicLinkRepository = filePublicLinkRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
        this.messages = messages;
    }

    private String uploadTooLargeMessage(long maxBytes) {
        if (maxBytes >= 1024 * 1024) {
            return messages.format("error.file.upload_too_large_mb", maxBytes / (1024 * 1024));
        }
        return messages.format("error.file.upload_too_large_bytes", maxBytes);
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Upload file (raw body)",
        description = "Тело = байты файла; заголовки Content-Type и X-Filename")
    @ApiResponse(responseCode = "201", description = "File uploaded",
        content = @Content(schema = @Schema(implementation = FileUploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Upload failed",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response upload(byte[] data,
                           @Context jakarta.ws.rs.core.HttpHeaders headers,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var filename = headers.getHeaderString("X-Filename");
        var mimeType = headers.getHeaderString("Content-Type");
        if (mimeType == null || mimeType.equals(MediaType.APPLICATION_OCTET_STREAM)) {
            mimeType = "application/octet-stream";
        }
        if (data == null || data.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.empty_file")))
                .build();
        }
        var result = fileService.upload(new java.io.ByteArrayInputStream(data),
            filename, mimeType, data.length, userId);
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload file (multipart)",
        description = "Поле формы с именем **file** (удобно для браузера)")
    @ApiResponse(responseCode = "201", description = "File uploaded",
        content = @Content(schema = @Schema(implementation = FileUploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Upload failed",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response uploadMultipart(@FormDataParam("file") FormDataBodyPart filePart,
                                    @Context SecurityContext securityContext) {
        if (filePart == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.missing_form_field_file")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var filename = filePart.getContentDisposition() != null
            ? filePart.getContentDisposition().getFileName()
            : null;
        var mimeType = filePart.getMediaType() != null
            ? filePart.getMediaType().toString()
            : "application/octet-stream";
        var raw = filePart.getEntity();
        if (!(raw instanceof InputStream is)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.multipart_must_be_stream")))
                .build();
        }
        try (is) {
            var result = fileService.uploadStream(is, filename, mimeType, userId);
            if (result == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                    .build();
            }
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.format("error.file.upload_failed", e.getMessage())))
                .build();
        }
    }

    @GET
    @Path("/{fileId}")
    @Operation(summary = "File info", description = "Метаданные: владелец или участник чата с не-E2EE сообщением, content = file id")
    @ApiResponse(responseCode = "200", description = "File metadata",
        content = @Content(schema = @Schema(implementation = FileInfoResponse.class)))
    @ApiResponse(responseCode = "403", description = "Not the file owner",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "File not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response getInfo(@PathParam("fileId") String fileId,
                            @Context SecurityContext securityContext) {
        var fid = UuidParams.required(fileId, "file_id");
        var userId = CurrentUserId.uuid(securityContext);
        var info = fileService.getInfo(fileId);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.not_found")))
                .build();
        }
        if (!fileService.mayViewFile(info, fid, userId)) {
            ApiDeniedMetrics.fileAccessDenied();
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.file.not_allowed")))
                .build();
        }
        return Response.ok(info).build();
    }

    @GET
    @Path("/{fileId}/message-ref")
    @Operation(summary = "Message with file", description = "Последнее видимое сообщение чата с файлом (content или attachment_file_id)")
    @ApiResponse(responseCode = "200", description = "Chat and message ids",
        content = @Content(schema = @Schema(implementation = FileMessageRefResponse.class)))
    public Response messageRef(@PathParam("fileId") String fileIdStr,
                               @Context SecurityContext securityContext) {
        var fileId = UuidParams.required(fileIdStr, "file_id");
        var userId = CurrentUserId.uuid(securityContext);
        var ref = fileService.findMessageRefForViewer(fileId, userId);
        if (ref.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.message_ref_not_found")))
                .build();
        }
        var r = ref.get();
        return Response.ok(new FileMessageRefResponse(r.chatId().toString(), r.messageId().toString())).build();
    }

    @GET
    @Path("/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download file", description = "Скачивание: владелец или участник чата с доступом к файлу")
    @ApiResponse(responseCode = "403", description = "Not the file owner",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response download(@PathParam("fileId") String fileId,
                             @Context SecurityContext securityContext) {
        var fid = UuidParams.required(fileId, "file_id");
        var userId = CurrentUserId.uuid(securityContext);
        var info = fileService.getInfo(fileId);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!fileService.mayViewFile(info, fid, userId)) {
            ApiDeniedMetrics.fileAccessDenied();
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.file.not_allowed")))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        var stream = fileService.download(fileId);
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream)
            .header("Content-Disposition", "attachment; filename=\"" + info.filename() + "\"")
            .header("Content-Type", info.mimeType())
            .build();
    }

    @DELETE
    @Path("/{fileId}")
    @Operation(summary = "Delete file", description = "Удаление только владельцем")
    @ApiResponse(responseCode = "204", description = "File deleted")
    @ApiResponse(responseCode = "403", description = "Not the file owner",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "File not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response delete(@PathParam("fileId") String fileId,
                           @Context SecurityContext securityContext) {
        UuidParams.required(fileId, "file_id");
        var userId = CurrentUserId.uuid(securityContext);
        var info = fileService.getInfo(fileId);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.not_found")))
                .build();
        }
        if (!info.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.file.not_allowed")))
                .build();
        }
        var ok = fileService.delete(fileId);
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/public-links")
    @Operation(summary = "List my active public links", description = "Все активные публичные ссылки текущего пользователя")
    @ApiResponse(responseCode = "200", description = "List of links",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerPublicLinkSummary.class))))
    public Response listMyPublicLinks(@QueryParam("limit") @DefaultValue("50") int limit,
                                      @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var links = filePublicLinkRepository.listActiveByOwner(userId, limit);
        return Response.ok(links).build();
    }

    @GET
    @Path("/{fileId}/public-links")
    @Operation(summary = "List active public links", description = "Активные (не отозванные, не истёкшие) ссылки владельца файла")
    @ApiResponse(responseCode = "200", description = "List of links",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PublicLinkSummary.class))))
    public Response listPublicLinks(@PathParam("fileId") String fileIdStr,
                                    @Context SecurityContext securityContext) {
        var fileId = UuidParams.required(fileIdStr, "file_id");
        var userId = CurrentUserId.uuid(securityContext);
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.file.not_allowed")))
                .build();
        }
        var links = filePublicLinkRepository.listActiveByFileAndOwner(fileId, userId);
        return Response.ok(links).build();
    }

    @POST
    @Path("/{fileId}/public-links")
    @Operation(summary = "Create public link A/B/C", description = "ТЗ п. 15: публичные ссылки; пароль только для kind C")
    public Response createPublicLink(@PathParam("fileId") String fileIdStr,
                                     CreatePublicLinkRequest request,
                                     @Context SecurityContext securityContext) {
        if (request == null || request.linkKind() == null || request.linkKind().length() != 1) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.link_kind_invalid")))
                .build();
        }
        char kind = Character.toUpperCase(request.linkKind().charAt(0));
        if (kind != 'A' && kind != 'B' && kind != 'C') {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.link_kind_invalid")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var fileId = UuidParams.required(fileIdStr, "file_id");
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.file.not_allowed")))
                .build();
        }
        long ttlSec = request.ttlSeconds() != null && request.ttlSeconds() > 0
            ? request.ttlSeconds()
            : appConfig.filePublicLinkDefaultTtlSeconds();
        var expires = clock.instant().plus(ttlSec, ChronoUnit.SECONDS);
        var created = filePublicLinkRepository.insert(fileId, userId, kind, request.password(), expires);
        if (created.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.link_create_kind_c_password")))
                .build();
        }
        var c = created.get();
        auditRepository.record(userId, "file.public_link.create", "file", fileIdStr,
            publicLinkCreateAuditDetails(c.id(), kind));
        var hint = "/api/v1/files/pub/" + c.rawToken() + (kind == 'B' ? " (use /auth-link/ with Bearer for kind B)" : "");
        return Response.status(Response.Status.CREATED)
            .entity(new PublicLinkCreatedResponse(c.id(), c.rawToken(), String.valueOf(kind), c.expiresAt(), hint))
            .build();
    }

    @DELETE
    @Path("/{fileId}/public-links/{linkId}")
    @Operation(summary = "Отозвать публичную ссылку", description = "Повторный доступ по токену вернёт 404")
    public Response revokePublicLink(@PathParam("fileId") String fileIdStr,
                                    @PathParam("linkId") String linkIdStr,
                                    @Context SecurityContext securityContext) {
        var fileId = UuidParams.required(fileIdStr, "file_id");
        var linkId = UuidParams.required(linkIdStr, "link_id");
        var userId = CurrentUserId.uuid(securityContext);
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN).entity(new ApiError(403, messages.get("error.file.not_allowed"))).build();
        }
        if (!filePublicLinkRepository.revoke(userId, fileId, linkId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.link_not_found_revoked")))
                .build();
        }
        auditRepository.record(userId, "file.public_link.revoke", "file", fileIdStr,
            publicLinkRevokeAuditDetails(linkId));
        return Response.noContent().build();
    }

    @GET
    @Path("/pub/{token}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download via public link (A or C)")
    public Response downloadPublic(@PathParam("token") String rawToken,
                                   @QueryParam("password") String password) {
        return resolvePublicDownload(rawToken, password, null);
    }

    @GET
    @Path("/auth-link/{token}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download via kind B (authenticated)")
    public Response downloadAuthLink(@PathParam("token") String rawToken,
                                   @QueryParam("password") String password,
                                   @Context SecurityContext securityContext) {
        var userId = securityContext.getUserPrincipal() != null
            ? securityContext.getUserPrincipal().getName()
            : null;
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(new ApiError(401, messages.get("error.file.bearer_required"))).build();
        }
        return resolvePublicDownload(rawToken, password, userId);
    }

    private Response resolvePublicDownload(String rawToken, String password, String bearerUserId) {
        if (rawToken == null || rawToken.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var hash = FilePublicLinkRepository.sha256Hex(rawToken);
        var resolved = filePublicLinkRepository.findValidByTokenHash(hash);
        if (resolved.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var link = resolved.get();
        var fid = link.fileId().toString();
        var info = fileService.getInfo(fid);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        switch (link.linkKind()) {
            case 'A':
                break;
            case 'B':
                if (bearerUserId == null) {
                    return Response.status(Response.Status.UNAUTHORIZED).entity(new ApiError(401, messages.get("error.file.bearer_required"))).build();
                }
                break;
            case 'C':
                if (password == null || link.passwordHash() == null
                    || !link.passwordHash().equals(FilePublicLinkRepository.sha256Hex(password))) {
                    return Response.status(Response.Status.FORBIDDEN).entity(new ApiError(403, messages.get("error.file.password_required"))).build();
                }
                break;
            default:
                return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var stream = fileService.download(fid);
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream)
            .header("Content-Disposition", "attachment; filename=\"" + info.filename() + "\"")
            .header("Content-Type", info.mimeType())
            .build();
    }
}
