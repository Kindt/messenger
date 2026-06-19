package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.export.ExportZipManifestReader;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared export artifact download for chat owner API and admin API. */
public final class ExportDownloadSupport {

    public static final String AUDIT_USER_DOWNLOAD = "export.downloaded";
    public static final String AUDIT_ADMIN_DOWNLOAD = "export.admin_downloaded";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExportDownloadSupport() {}

    public static Response download(
        ExportJobPort.ExportJobRow job,
        UUID chatId,
        UUID jobId,
        UUID actorUserId,
        String auditAction,
        ExportFileAccess exportFileAccess,
        AuditPort auditPort,
        UserMessageSource messages,
        String part,
        String fileIdStr,
        String fileIdsStr
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
        final ExportFileAccess.DownloadPart downloadPart;
        try {
            downloadPart = ExportFileAccess.DownloadPart.parse(part);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.export.invalid_download_part")))
                .build();
        }
        UUID fileId = null;
        List<UUID> fileIds = List.of();
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            if (fileIdStr == null || fileIdStr.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.file_id_required")))
                    .build();
            }
            fileId = UuidParams.required(fileIdStr, "file_id");
        } else if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            fileIds = parseFileIds(fileIdsStr);
            if (fileIds.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.file_ids_required")))
                    .build();
            }
            if (fileIds.size() > ExportFileAccess.MAX_BINARIES_FILE_IDS) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.too_many_file_ids")))
                    .build();
            }
        }
        if (!exportFileAccess.canDownloadPart(job, downloadPart)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.file_not_found")))
                .build();
        }
        ExportZipManifestReader.AttachmentRef binaryRef = null;
        ExportFileAccess.BinariesResolution binariesResolution = null;
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            try {
                var resolved = exportFileAccess.resolveBinaryAttachment(job, fileId);
                if (resolved.isEmpty()) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiError(404, messages.get("error.export.attachment_not_in_bundle")))
                        .build();
                }
                binaryRef = resolved.get();
            } catch (java.io.IOException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get("error.export.file_not_found")))
                    .build();
            }
        } else if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            try {
                binariesResolution = exportFileAccess.resolveBinaries(job, fileIds);
                if (!binariesResolution.complete()) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiError(404, messages.get("error.export.attachments_not_in_bundle")))
                        .build();
                }
            } catch (java.io.IOException e) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get("error.export.file_not_found")))
                    .build();
            }
        }
        var target = ExportFileAccess.downloadTarget(job, downloadPart, binaryRef);
        auditPort.record(
            actorUserId,
            auditAction,
            "export_job",
            jobId.toString(),
            downloadAuditDetails(chatId, downloadPart, job, fileId, fileIds));
        StreamingOutput body;
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            var zipPath = binaryRef.zipPath();
            body = out -> exportFileAccess.streamAttachmentEntry(
                job, zipPath, stream -> ExportFileAccess.transferToOutput(stream, out));
        } else if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            var attachments = binariesResolution.attachments();
            body = out -> exportFileAccess.streamBinariesZip(job, attachments, out);
        } else {
            body = out -> exportFileAccess.streamDownloadPart(
                job, downloadPart, stream -> ExportFileAccess.transferToOutput(stream, out));
        }
        return Response.ok(body)
            .type(target.mediaType())
            .header("Content-Disposition", "attachment; filename=\"" + target.fileName() + "\"")
            .build();
    }

    private static List<UUID> parseFileIds(String fileIdsStr) {
        if (fileIdsStr == null || fileIdsStr.isBlank()) {
            return List.of();
        }
        var out = new ArrayList<UUID>();
        for (var token : fileIdsStr.split(",")) {
            var trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(UuidParams.required(trimmed, "file_id"));
        }
        return List.copyOf(out);
    }

    private static String downloadAuditDetails(
        UUID chatId,
        ExportFileAccess.DownloadPart part,
        ExportJobPort.ExportJobRow job,
        UUID fileId,
        List<UUID> fileIds
    ) {
        try {
            var node = MAPPER.createObjectNode()
                .put("chat_id", chatId.toString())
                .put("part", part.name().toLowerCase())
                .put("output_format", ExportOutputRef.outputFormat(job.outputPath()))
                .put("output_storage", ExportOutputRef.outputStorage(job.outputPath()))
                .put("status", job.status());
            if (fileId != null) {
                node.put("file_id", fileId.toString());
            }
            if (fileIds != null && !fileIds.isEmpty()) {
                var arr = node.putArray("file_ids");
                for (var id : fileIds) {
                    arr.add(id.toString());
                }
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"chat_id\":\"" + chatId + "\",\"part\":\"" + part.name().toLowerCase() + "\"}";
        }
    }
}
