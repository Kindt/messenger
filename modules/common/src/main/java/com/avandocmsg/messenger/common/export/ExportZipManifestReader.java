package com.avandocmsg.messenger.common.export;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Parses {@link ExportOutputRef#ZIP_ATTACHMENTS_MANIFEST} inside export zip bundles. */
public final class ExportZipManifestReader {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ExportZipManifestReader() {}

    public record AttachmentRef(String zipPath, String downloadFileName, String mediaType) {}

    public record ManifestEntry(
        String fileId,
        String filename,
        String mimeType,
        String zipPath,
        long sizeBytes,
        String sha256
    ) {}

    public static Map<String, ManifestEntry> indexManifestEntries(InputStream manifestJson) throws IOException {
        var root = MAPPER.readTree(manifestJson);
        var files = root.get("files");
        if (files == null || !files.isArray()) {
            return Map.of();
        }
        var index = new LinkedHashMap<String, ManifestEntry>();
        for (var file : files) {
            var fileId = file.path("fileId").asText(null);
            if (fileId == null || fileId.isBlank()) {
                continue;
            }
            var zipPath = file.path("zipPath").asText(null);
            if (zipPath == null || zipPath.isBlank() || zipPath.contains("..")) {
                continue;
            }
            var filename = file.path("filename").asText("");
            var mime = file.path("mimeType").asText("application/octet-stream");
            index.put(
                fileId,
                new ManifestEntry(
                    fileId,
                    filename != null ? filename : "",
                    mime,
                    zipPath,
                    file.path("sizeBytes").asLong(0),
                    file.path("sha256").asText("")));
        }
        return Collections.unmodifiableMap(index);
    }

    public static Map<String, AttachmentRef> indexByFileId(InputStream manifestJson) throws IOException {
        var index = new LinkedHashMap<String, AttachmentRef>();
        for (var entry : indexManifestEntries(manifestJson).values()) {
            var downloadName = entry.filename() != null && !entry.filename().isBlank()
                ? entry.filename()
                : entry.fileId();
            index.put(entry.fileId(), new AttachmentRef(entry.zipPath(), downloadName, entry.mimeType()));
        }
        return Collections.unmodifiableMap(index);
    }

    public static Optional<AttachmentRef> findFile(InputStream manifestJson, String fileId) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indexByFileId(manifestJson).get(fileId));
    }
}
