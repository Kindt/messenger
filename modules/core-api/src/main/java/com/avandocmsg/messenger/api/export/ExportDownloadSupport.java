package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.common.json.MessengerJson;
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

    private static final String FILE_ID_PARAM = "file_id";
    private static final String ERR_FILE_NOT_FOUND = "error.export.file_not_found";

    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ExportDownloadSupport() {}

    /** Inputs for a single export download request (job + part selection). */
    public record DownloadRequest(
        ExportJobPort.ExportJobRow job,
        UUID chatId,
        UUID jobId,
        UUID actorUserId,
        String auditAction,
        String part,
        String fileIdStr,
        String fileIdsStr
    ) {}

    public static Response download(
        DownloadRequest request,
        ExportFileAccess exportFileAccess,
        AuditPort auditPort,
        UserMessageSource messages
    ) {
        var readiness = checkDownloadReady(request.job(), exportFileAccess, messages);
        if (readiness != null) {
            return readiness;
        }
        final ExportFileAccess.DownloadPart downloadPart;
        try {
            downloadPart = ExportFileAccess.DownloadPart.parse(request.part());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.export.invalid_download_part")))
                .build();
        }
        var selection = parsePartSelection(downloadPart, request.fileIdStr(), request.fileIdsStr(), messages);
        if (selection.error() != null) {
            return selection.error();
        }
        if (!exportFileAccess.canDownloadPart(request.job(), downloadPart)) {
            return fileNotFound(messages);
        }
        var resolved = resolveAttachments(exportFileAccess, request.job(), downloadPart, selection, messages);
        if (resolved.error() != null) {
            return resolved.error();
        }
        var target = ExportFileAccess.downloadTarget(request.job(), downloadPart, resolved.binaryRef());
        auditPort.record(
            request.actorUserId(),
            request.auditAction(),
            "export_job",
            request.jobId().toString(),
            downloadAuditDetails(
                request.chatId(), downloadPart, request.job(), selection.fileId(), selection.fileIds()));
        return Response.ok(streamBody(exportFileAccess, request.job(), downloadPart, resolved))
            .type(target.mediaType())
            .header("Content-Disposition", "attachment; filename=\"" + target.fileName() + "\"")
            .build();
    }

    private static Response checkDownloadReady(
        ExportJobPort.ExportJobRow job,
        ExportFileAccess exportFileAccess,
        UserMessageSource messages
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
        return null;
    }

    private record PartSelection(UUID fileId, List<UUID> fileIds, Response error) {}

    private static PartSelection parsePartSelection(
        ExportFileAccess.DownloadPart downloadPart,
        String fileIdStr,
        String fileIdsStr,
        UserMessageSource messages
    ) {
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            if (fileIdStr == null || fileIdStr.isBlank()) {
                return new PartSelection(null, List.of(), Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.file_id_required")))
                    .build());
            }
            return new PartSelection(UuidParams.required(fileIdStr, FILE_ID_PARAM), List.of(), null);
        }
        if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            var fileIds = parseFileIds(fileIdsStr);
            if (fileIds.isEmpty()) {
                return new PartSelection(null, List.of(), Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.file_ids_required")))
                    .build());
            }
            if (fileIds.size() > ExportFileAccess.MAX_BINARIES_FILE_IDS) {
                return new PartSelection(null, List.of(), Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.export.too_many_file_ids")))
                    .build());
            }
            return new PartSelection(null, fileIds, null);
        }
        return new PartSelection(null, List.of(), null);
    }

    private record ResolvedAttachments(
        ExportZipManifestReader.AttachmentRef binaryRef,
        ExportFileAccess.BinariesResolution binariesResolution,
        Response error
    ) {}

    private static ResolvedAttachments resolveAttachments(
        ExportFileAccess exportFileAccess,
        ExportJobPort.ExportJobRow job,
        ExportFileAccess.DownloadPart downloadPart,
        PartSelection selection,
        UserMessageSource messages
    ) {
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            try {
                var resolved = exportFileAccess.resolveBinaryAttachment(job, selection.fileId());
                if (resolved.isEmpty()) {
                    return new ResolvedAttachments(null, null, Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiError(404, messages.get("error.export.attachment_not_in_bundle")))
                        .build());
                }
                return new ResolvedAttachments(resolved.get(), null, null);
            } catch (java.io.IOException e) {
                return new ResolvedAttachments(null, null, fileNotFound(messages));
            }
        }
        if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            try {
                var binariesResolution = exportFileAccess.resolveBinaries(job, selection.fileIds());
                if (!binariesResolution.complete()) {
                    return new ResolvedAttachments(null, null, Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiError(404, messages.get("error.export.attachments_not_in_bundle")))
                        .build());
                }
                return new ResolvedAttachments(null, binariesResolution, null);
            } catch (java.io.IOException e) {
                return new ResolvedAttachments(null, null, fileNotFound(messages));
            }
        }
        return new ResolvedAttachments(null, null, null);
    }

    private static StreamingOutput streamBody(
        ExportFileAccess exportFileAccess,
        ExportJobPort.ExportJobRow job,
        ExportFileAccess.DownloadPart downloadPart,
        ResolvedAttachments resolved
    ) {
        if (downloadPart == ExportFileAccess.DownloadPart.BINARY) {
            var zipPath = resolved.binaryRef().zipPath();
            return out -> exportFileAccess.streamAttachmentEntry(
                job, zipPath, stream -> ExportFileAccess.transferToOutput(stream, out));
        }
        if (downloadPart == ExportFileAccess.DownloadPart.BINARIES) {
            var attachments = resolved.binariesResolution().attachments();
            return out -> exportFileAccess.streamBinariesZip(job, attachments, out);
        }
        return out -> exportFileAccess.streamDownloadPart(
            job, downloadPart, stream -> ExportFileAccess.transferToOutput(stream, out));
    }

    private static Response fileNotFound(UserMessageSource messages) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ApiError(404, messages.get(ERR_FILE_NOT_FOUND)))
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
            out.add(UuidParams.required(trimmed, FILE_ID_PARAM));
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
                node.put(FILE_ID_PARAM, fileId.toString());
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
