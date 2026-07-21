package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds {@code .export.zip}: {@code export.json} + {@code attachments/{fileId}/{filename}} + manifest. */
final class ExportFileBundleBuilder {

    private static final Logger log = LoggerFactory.getLogger(ExportFileBundleBuilder.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    static final String ATTACHMENTS_PREFIX = "attachments/";

    private ExportFileBundleBuilder() {
    }

    static FileBundleStats build(
        ObjectNode exportRoot,
        Path zipPath,
        ExportFileBodyFetcher fetcher,
        int maxFiles,
        long maxBytesPerFile,
        UserMessageSource workerMessages
    ) throws IOException {
        Files.createDirectories(zipPath.getParent());
        var manifestEntries = MAPPER.createArrayNode();
        int included = 0;
        int skipped = 0;
        long includedBytes = 0L;
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            var files = exportRoot.path("referencedFiles");
            if (files.isArray()) {
                var counts = collectAttachments(
                    (ArrayNode) files, fetcher, maxFiles, maxBytesPerFile, zos, manifestEntries, workerMessages);
                included = counts.included();
                skipped = counts.skipped();
                includedBytes = counts.includedBytes();
            }
            // Always write attachments/manifest.json so download?part=manifest is stable (even 0 files).
            writeManifestEntry(zos, manifestEntries, included, skipped, includedBytes);
            var stats = new FileBundleStats(true, maxFiles, maxBytesPerFile, included, skipped, includedBytes);
            exportRoot.set("fileBodies", stats.toJsonNode());
            exportRoot.put("attachmentManifestPath", ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST);
            patchFileBinaryGdpr(exportRoot, included > 0);
            writePackageManifest(zos, exportRoot, stats);
            ExportArtifactWriter.writePrettyJsonZipEntry(zos, ExportOutputRef.ZIP_JSON_ENTRY, exportRoot);
            log.info(workerMessages.format("worker.export_replay.zip_built",
                zipPath.getFileName(), included, skipped, includedBytes));
            return stats;
        }
    }

    private static void writePackageManifest(
        ZipOutputStream zos,
        ObjectNode exportRoot,
        FileBundleStats stats
    ) throws IOException {
        var manifest = MAPPER.createObjectNode();
        manifest.put("formatVersion", 1);
        manifest.put("exportJsonEntry", ExportOutputRef.ZIP_JSON_ENTRY);
        manifest.put("attachmentsManifestPath", ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST);
        manifest.put("includedAttachmentCount", stats.includedCount());
        manifest.put("skippedAttachmentCount", stats.skippedCount());
        manifest.put("includedAttachmentBytes", stats.includedBytes());
        var completeness = exportRoot.get("exportCompleteness");
        if (completeness != null && !completeness.isNull()) {
            manifest.set("exportCompleteness", completeness.deepCopy());
        }
        writePrettyManifestEntry(zos, ExportOutputRef.ZIP_PACKAGE_MANIFEST, manifest);
        exportRoot.put("packageManifestPath", ExportOutputRef.ZIP_PACKAGE_MANIFEST);
    }

    private static void writePrettyManifestEntry(ZipOutputStream zos, String entryName, JsonNode manifest)
        throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        ExportArtifactWriter.writePrettyJson(zos, manifest);
        zos.closeEntry();
    }

    private static void writeManifestEntry(
        ZipOutputStream zos,
        ArrayNode entries,
        int included,
        int skipped,
        long includedBytes
    ) throws IOException {
        var manifest = MAPPER.createObjectNode();
        manifest.put("formatVersion", 1);
        manifest.put("includedCount", included);
        manifest.put("skippedCount", skipped);
        manifest.put("includedBytes", includedBytes);
        manifest.set("files", entries);
        writePrettyManifestEntry(zos, ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST, manifest);
    }

    private record AttachCounts(int included, int skipped, long includedBytes) {
    }

    private static AttachCounts collectAttachments(
        ArrayNode referencedFiles,
        ExportFileBodyFetcher fetcher,
        int maxFiles,
        long maxBytesPerFile,
        ZipOutputStream zos,
        ArrayNode manifestEntries,
        UserMessageSource workerMessages
    ) throws IOException {
        int included = 0;
        int skipped = 0;
        long includedBytes = 0L;
        for (var node : referencedFiles) {
            if (included >= maxFiles) {
                skipped++;
                continue;
            }
            if (!node.isObject()) {
                skipped++;
                continue;
            }
            var id = node.path("id").asText(null);
            var filename = node.path("filename").asText(null);
            var mimeType = node.path("mimeType").asText(null);
            if (id == null || id.isBlank()) {
                skipped++;
                continue;
            }
            var opened = fetcher.open(id, filename, maxBytesPerFile);
            if (opened.isEmpty()) {
                skipped++;
                continue;
            }
            try (var in = new BufferedInputStream(opened.get().stream())) {
                var safeName = safeAttachmentFileName(filename);
                var entryName = ATTACHMENTS_PREFIX + id + "/" + safeName;
                var digest = MessageDigest.getInstance("SHA-256");
                zos.putNextEntry(new ZipEntry(entryName));
                long written;
                try (var digestIn = new DigestInputStream(in, digest)) {
                    written = digestIn.transferTo(zos);
                }
                zos.closeEntry();
                includedBytes += written;
                included++;
                var sha256 = HexFormat.of().formatHex(digest.digest());
                if (node instanceof ObjectNode obj) {
                    obj.put("exportAttachmentPath", entryName);
                    obj.put("exportBodyIncluded", true);
                    obj.put("exportBodySha256", sha256);
                    obj.put("exportBodySizeBytes", written);
                }
                var manifestRow = manifestEntries.addObject();
                manifestRow.put("fileId", id);
                manifestRow.put("filename", filename != null ? filename : "");
                manifestRow.put("mimeType", mimeType != null ? mimeType : opened.get().contentType());
                manifestRow.put("zipPath", entryName);
                manifestRow.put("sizeBytes", written);
                manifestRow.put("sha256", sha256);
            } catch (Exception e) {
                skipped++;
                log.debug(workerMessages.format("worker.export_replay.attachment_skip", id, e.getMessage()));
            }
        }
        return new AttachCounts(included, skipped, includedBytes);
    }

    static String safeAttachmentFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        var name = filename.replace('\\', '/');
        var slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "file" : name;
    }

    private static void patchFileBinaryGdpr(ObjectNode exportRoot, boolean included) {
        var completeness = exportRoot.get("exportCompleteness");
        if (completeness == null || !completeness.isObject()) {
            return;
        }
        var gdpr = completeness.get("gdprDisclosures");
        if (gdpr == null || !gdpr.isArray()) {
            return;
        }
        for (var item : gdpr) {
            if (item.isObject() && "file_binary".equals(item.path("id").asText())) {
                ((ObjectNode) item).put("included", included);
                ((ObjectNode) item).put(
                    "note",
                    included
                        ? "Referenced attachment bytes included under attachments/ in the export zip; "
                            + "see attachments/manifest.json for SHA-256."
                        : "Attachment file bytes are not part of this export bundle."
                );
            }
        }
        ((ObjectNode) completeness).put("referencedFileBodiesIncluded", included);
        ((ObjectNode) completeness).put("attachmentManifestIncluded", included);
    }

    record FileBundleStats(
        boolean requested,
        int maxFiles,
        long maxBytesPerFile,
        int includedCount,
        int skippedCount,
        long includedBytes
    ) {
        ObjectNode toJsonNode() {
            var n = MAPPER.createObjectNode();
            n.put("requested", requested);
            n.put("maxFiles", maxFiles);
            n.put("maxBytesPerFile", maxBytesPerFile);
            n.put("includedCount", includedCount);
            n.put("skippedCount", skippedCount);
            n.put("includedBytes", includedBytes);
            n.put("manifestPath", ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST);
            return n;
        }
    }
}
