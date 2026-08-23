package com.avandocmsg.messenger.api.files;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.dto.CreatePublicLinkRequest;
import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.api.files.dto.FileMessageRefResponse;
import com.avandocmsg.messenger.api.files.dto.OwnerPublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.PublicLinkCreatedResponse;
import com.avandocmsg.messenger.api.files.dto.PublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.FilePresignUploadRequest;
import com.avandocmsg.messenger.api.files.dto.FilePresignUploadResponse;
import com.avandocmsg.messenger.api.files.dto.FileUploadResponse;
import com.avandocmsg.messenger.api.metrics.ApiDeniedMetrics;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.security.TimingSensitivePaths;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.application.AvatarAccessTokenService;
import com.avandocmsg.messenger.core.application.FileDomainMapper;
import com.avandocmsg.messenger.core.application.ImageResizeService;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
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
import java.net.URI;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Path("/v1/files")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Files", description = "File upload and download")
public class FileResource {

    private static final ObjectMapper FILE_AUDIT_JSON = MessengerJson.mapper();
    private static final String LINK_ID = "link_id";
    private static final String FILE_ID = "file_id";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String ERR_EMPTY_FILE = "error.file.empty_file";
    private static final String ERR_UPLOAD_FAILED = "error.file.upload_failed";
    private static final String ERR_NOT_FOUND = "error.file.not_found";
    private static final String ERR_NOT_ALLOWED = "error.file.not_allowed";

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
        n.put(LINK_ID, linkId);
        n.put("kind", String.valueOf(kind));
        return writeFileAuditJson(n);
    }

    /** Package-visible for tests. */
    static String publicLinkRevokeAuditDetails(UUID linkId) {
        var n = FILE_AUDIT_JSON.createObjectNode();
        n.put(LINK_ID, linkId.toString());
        return writeFileAuditJson(n);
    }

    private final FileService fileService;
    private final FileApplicationService fileApplicationService;
    private final AvatarApplicationService avatarApplicationService;
    private final AvatarAccessTokenService avatarAccessTokenService;
    private final AppConfig appConfig;
    private final PublicLinkPort publicLinkPort;
    private final AuditPort auditPort;
    private final Clock clock;
    private final UserMessageSource messages;

    @Inject
    public FileResource(FileService fileService, FileApplicationService fileApplicationService,
                          AvatarApplicationService avatarApplicationService,
                          AvatarAccessTokenService avatarAccessTokenService,
                          AppConfig appConfig,
                          PublicLinkPort publicLinkPort,
                          AuditPort auditPort, Clock clock, UserMessageSource messages) {
        this.fileService = fileService;
        this.fileApplicationService = fileApplicationService;
        this.avatarApplicationService = avatarApplicationService;
        this.avatarAccessTokenService = avatarAccessTokenService;
        this.appConfig = appConfig;
        this.publicLinkPort = publicLinkPort;
        this.auditPort = auditPort;
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
    public Response upload(InputStream data,
                           @Context jakarta.ws.rs.core.HttpHeaders headers,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var filename = headers.getHeaderString("X-Filename");
        var mimeType = headers.getHeaderString(HEADER_CONTENT_TYPE);
        if (mimeType == null || mimeType.equals(MediaType.APPLICATION_OCTET_STREAM)) {
            var inferred = ImageResizeService.inferImageMimeFromFilename(filename);
            mimeType = inferred != null ? inferred : "application/octet-stream";
        }
        if (data == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_EMPTY_FILE)))
                .build();
        }
        long contentLength = -1;
        var clHeader = headers.getHeaderString("Content-Length");
        if (clHeader != null && !clHeader.isBlank()) {
            try {
                contentLength = Long.parseLong(clHeader.trim());
            } catch (NumberFormatException ignored) {
                contentLength = -1;
            }
        }
        if (contentLength == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_EMPTY_FILE)))
                .build();
        }
        if (contentLength > fileService.maxUploadBytes()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                .build();
        }
        try {
            FileUploadResponse result;
            if (contentLength >= 0) {
                result = fileService.upload(data, filename, mimeType, contentLength, userId);
            } else {
                result = fileService.uploadStream(data, filename, mimeType, userId);
            }
            if (result == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                    .build();
            }
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.format(ERR_UPLOAD_FAILED, e.getMessage())))
                .build();
        }
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
        try {
            var raw = filePart.getEntity();
            if (raw instanceof byte[] bytes) {
                var result = fileService.upload(
                    new java.io.ByteArrayInputStream(bytes), filename, mimeType, bytes.length, userId);
                if (result == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                        .build();
                }
                return Response.status(Response.Status.CREATED).entity(result).build();
            }
            try (InputStream is = filePart.getValueAs(InputStream.class)) {
                if (is == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiError(400, messages.get("error.file.multipart_must_be_stream")))
                        .build();
                }
                var result = fileService.uploadStream(is, filename, mimeType, userId);
                if (result == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                        .build();
                }
                return Response.status(Response.Status.CREATED).entity(result).build();
            }
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.format(ERR_UPLOAD_FAILED, e.getMessage())))
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
        return TimingSensitivePaths.respond(appConfig, () -> {
            var fid = UuidParams.required(fileId, FILE_ID);
            var userId = CurrentUserId.uuid(securityContext);
            var fileIdDomain = FileId.of(fid);
            if (fileApplicationService.findById(fileIdDomain).isEmpty()) {
                TimingSensitivePaths.padNotFound(appConfig);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get(ERR_NOT_FOUND)))
                    .build();
            }
            var info = fileApplicationService
                .getMetadataForUser(UserId.of(userId), fileIdDomain)
                .map(FileDomainMapper::toResponse)
                .orElse(null);
            if (info == null) {
                ApiDeniedMetrics.fileAccessDenied();
                TimingSensitivePaths.padNotFound(appConfig);
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                    .build();
            }
            return Response.ok(info).build();
        });
    }

    @GET
    @Path("/{fileId}/message-ref")
    @Operation(summary = "Message with file", description = "Последнее видимое сообщение чата с файлом (content или attachment_file_id)")
    @ApiResponse(responseCode = "200", description = "Chat and message ids",
        content = @Content(schema = @Schema(implementation = FileMessageRefResponse.class)))
    public Response messageRef(@PathParam("fileId") String fileIdStr,
                               @Context SecurityContext securityContext) {
        var fileId = UuidParams.required(fileIdStr, FILE_ID);
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
    @Path("/{fileId}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download file content (presigned redirect)",
        description = "307 redirect to MinIO presigned URL when enabled; otherwise streams bytes")
    public Response downloadContent(@PathParam("fileId") String fileId,
                                    @Context SecurityContext securityContext) {
        return download(fileId, securityContext);
    }

    @GET
    @Path("/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download file", description = "Скачивание: владелец или участник чата с доступом к файлу")
    @ApiResponse(responseCode = "403", description = "Not the file owner",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response download(@PathParam("fileId") String fileId,
                             @Context SecurityContext securityContext) {
        var fid = UuidParams.required(fileId, FILE_ID);
        var userId = CurrentUserId.uuid(securityContext);
        var fileIdDomain = FileId.of(fid);
        var meta = fileApplicationService.getMetadataForUser(UserId.of(userId), fileIdDomain);
        if (meta.isEmpty()) {
            if (fileApplicationService.findById(fileIdDomain).isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            ApiDeniedMetrics.fileAccessDenied();
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        var info = FileDomainMapper.toResponse(meta.get());
        if (appConfig.filePresignRedirectEnabled()) {
            var presigned = fileApplicationService.presignedDownloadUrl(
                fileIdDomain, appConfig.minioPresignTtlSeconds());
            if (presigned.isPresent()) {
                return Response.status(Response.Status.TEMPORARY_REDIRECT)
                    .location(URI.create(presigned.get()))
                    .header("Cache-Control", appConfig.filePresignRedirectCacheControl())
                    .build();
            }
        }
        var stream = fileApplicationService.download(fileIdDomain);
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream)
            .header(HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + info.filename() + "\"")
            .header(HEADER_CONTENT_TYPE, info.mimeType())
            .build();
    }

    @POST
    @Path("/presign-upload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Presigned PUT upload URL",
        description = "Client uploads bytes to MinIO then calls confirm-presigned-upload")
    @ApiResponse(responseCode = "201", description = "Presigned upload issued",
        content = @Content(schema = @Schema(implementation = FilePresignUploadResponse.class)))
    public Response presignUpload(FilePresignUploadRequest body,
                                  @Context SecurityContext securityContext) {
        if (body == null || body.size() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_EMPTY_FILE)))
                .build();
        }
        if (body.size() > fileService.maxUploadBytes()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, uploadTooLargeMessage(fileService.maxUploadBytes())))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var ttl = appConfig.minioPresignTtlSeconds();
        var result = fileApplicationService.beginPresignedUpload(
            body.filename(), body.mimeType(), body.size(), UserId.of(userId), ttl);
        if (result.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_UPLOAD_FAILED)))
                .build();
        }
        var r = result.get();
        return Response.status(Response.Status.CREATED)
            .entity(new FilePresignUploadResponse(
                r.file().id().value().toString(),
                r.uploadUrl(),
                r.downloadUrl(),
                r.expiresInSeconds()))
            .build();
    }

    @POST
    @Path("/{fileId}/confirm-presigned-upload")
    @Operation(summary = "Confirm presigned upload",
        description = "Verifies object exists in storage after direct MinIO PUT")
    @ApiResponse(responseCode = "200", description = "Upload confirmed",
        content = @Content(schema = @Schema(implementation = FileUploadResponse.class)))
    public Response confirmPresignedUpload(@PathParam("fileId") String fileId,
                                           @Context SecurityContext securityContext) {
        var fid = UuidParams.required(fileId, FILE_ID);
        var userId = CurrentUserId.uuid(securityContext);
        var fileIdDomain = FileId.of(fid);
        if (!fileApplicationService.confirmPresignedUpload(fileIdDomain, UserId.of(userId))) {
            if (fileApplicationService.findById(fileIdDomain).isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_UPLOAD_FAILED)))
                .build();
        }
        var info = fileApplicationService.findById(fileIdDomain)
            .map(FileDomainMapper::toResponse)
            .orElse(null);
        if (info == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new FileUploadResponse(
            info.id(),
            info.filename(),
            info.mimeType(),
            info.size(),
            "/api/v1/files/" + info.id() + "/download")).build();
    }

    @GET
    @Path("/{fileId}/resize")
    @Produces({"image/jpeg", MediaType.APPLICATION_JSON})
    @Operation(summary = "Resize image", description = "On-the-fly thumbnail (embedded mode); JPEG output; w/h capped by server config")
    @ApiResponse(responseCode = "200", description = "Resized JPEG")
    @ApiResponse(responseCode = "400", description = "Not an image or invalid dimensions",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Not allowed",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "File not found or resize disabled",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response resize(@PathParam("fileId") String fileId,
                           @QueryParam("w") @DefaultValue("200") int width,
                           @QueryParam("h") @DefaultValue("200") int height,
                           @QueryParam("avt") String avtToken,
                           @Context SecurityContext securityContext) {
        var fid = UuidParams.required(fileId, FILE_ID);
        if (!appConfig.fileResizeEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.resize_disabled")))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        var fileIdDomain = FileId.of(fid);
        var targetW = AvatarAccessTokenService.clampDimension(width);
        var targetH = AvatarAccessTokenService.clampDimension(height);
        java.util.Optional<com.avandocmsg.messenger.core.domain.StoredFile> meta = java.util.Optional.empty();
        if (securityContext.getUserPrincipal() != null) {
            var userId = CurrentUserId.uuid(securityContext);
            meta = fileApplicationService.getMetadataForUser(UserId.of(userId), fileIdDomain);
        } else if (avtToken != null && !avtToken.isBlank()
            && avatarApplicationService.verifyAvatarTokenAccess(
                avatarAccessTokenService, avtToken, fileIdDomain, targetW, targetH)) {
            meta = fileApplicationService.findById(fileIdDomain);
        }
        if (meta.isEmpty()) {
            if (fileApplicationService.findById(fileIdDomain).isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get(ERR_NOT_FOUND)))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            }
            ApiDeniedMetrics.fileAccessDenied();
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        var stored = meta.get();
        if (!ImageResizeService.isResizableFile(stored.mimeType(), stored.filename())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.resize_not_image")))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        var maxW = appConfig.fileResizeMaxWidth();
        var maxH = appConfig.fileResizeMaxHeight();
        var resizeW = Math.min(Math.max(targetW, 1), maxW);
        var resizeH = Math.min(Math.max(targetH, 1), maxH);
        var stream = fileApplicationService.download(fileIdDomain);
        if (stream == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get(ERR_NOT_FOUND)))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
        try (stream) {
            var resized = ImageResizeService.resizeToJpeg(
                stream, resizeW, resizeH, appConfig.fileResizeMaxSourcePixels());
            if (resized.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.file.resize_failed")))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            }
            var baseName = stored.filename();
            var dot = baseName.lastIndexOf('.');
            var thumbName = (dot > 0 ? baseName.substring(0, dot) : baseName) + "-thumb.jpg";
            return Response.ok(resized.get())
                .header(HEADER_CONTENT_DISPOSITION, "inline; filename=\"" + thumbName + "\"")
                .header(HEADER_CONTENT_TYPE, "image/jpeg")
                .header("Referrer-Policy", "no-referrer")
                .build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.resize_failed")))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
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
        var fid = UuidParams.required(fileId, FILE_ID);
        var userId = CurrentUserId.uuid(securityContext);
        var fileIdDomain = FileId.of(fid);
        var meta = fileApplicationService.findById(fileIdDomain);
        if (meta.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get(ERR_NOT_FOUND)))
                .build();
        }
        if (!meta.get().uploadedBy().value().equals(userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                .build();
        }
        if (!fileApplicationService.delete(fileIdDomain)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get(ERR_NOT_FOUND)))
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
        var links = publicLinkPort.listByOwner(UserId.of(userId), limit).stream()
            .map(e -> new OwnerPublicLinkSummary(
                e.id(), e.fileId(), e.linkKind(), e.expiresAt(), e.createdAt(), e.filename()))
            .toList();
        return Response.ok(links).build();
    }

    @GET
    @Path("/{fileId}/public-links")
    @Operation(summary = "List active public links", description = "Активные (не отозванные, не истёкшие) ссылки владельца файла")
    @ApiResponse(responseCode = "200", description = "List of links",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PublicLinkSummary.class))))
    public Response listPublicLinks(@PathParam("fileId") String fileIdStr,
                                    @Context SecurityContext securityContext) {
        var fileId = UuidParams.required(fileIdStr, FILE_ID);
        var userId = CurrentUserId.uuid(securityContext);
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                .build();
        }
        var links = publicLinkPort.listByFileAndOwner(FileId.of(fileId), UserId.of(userId)).stream()
            .map(e -> new PublicLinkSummary(e.id(), e.linkKind(), e.expiresAt(), e.createdAt()))
            .toList();
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
        var fileId = UuidParams.required(fileIdStr, FILE_ID);
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED)))
                .build();
        }
        long ttlSec = request.ttlSeconds() != null && request.ttlSeconds() > 0
            ? request.ttlSeconds()
            : appConfig.filePublicLinkDefaultTtlSeconds();
        var expires = clock.instant().plus(ttlSec, ChronoUnit.SECONDS);
        var created = publicLinkPort.createLink(FileId.of(fileId), UserId.of(userId), kind, request.password(), expires);
        if (created.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.file.link_create_kind_c_password")))
                .build();
        }
        var c = created.get();
        auditPort.record(userId, "file.public_link.create", "file", fileIdStr,
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
        var fileId = UuidParams.required(fileIdStr, FILE_ID);
        var linkId = UuidParams.required(linkIdStr, LINK_ID);
        var userId = CurrentUserId.uuid(securityContext);
        var meta = fileService.getInfo(fileIdStr);
        if (meta == null || !meta.uploadedBy().equals(userId.toString())) {
            return Response.status(Response.Status.FORBIDDEN).entity(new ApiError(403, messages.get(ERR_NOT_ALLOWED))).build();
        }
        if (!publicLinkPort.revokeLink(UserId.of(userId), FileId.of(fileId), linkId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.file.link_not_found_revoked")))
                .build();
        }
        auditPort.record(userId, "file.public_link.revoke", "file", fileIdStr,
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
        var hash = publicLinkPort.sha256Hex(rawToken);
        var resolved = publicLinkPort.findValidByTokenHash(hash);
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
                    || !link.passwordHash().equals(publicLinkPort.sha256Hex(password))) {
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
            .header(HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + info.filename() + "\"")
            .header(HEADER_CONTENT_TYPE, info.mimeType())
            .build();
    }
}
