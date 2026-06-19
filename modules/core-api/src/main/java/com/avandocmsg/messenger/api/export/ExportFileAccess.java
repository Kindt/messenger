package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ExportJobPort.ExportJobRow;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.export.ExportZipEntryReader;
import com.avandocmsg.messenger.common.export.ExportZipManifestReader;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streams export JSON from MinIO ({@code minio:…} in {@code output_path}) or local {@code EXPORT_DIR}.
 */
public class ExportFileAccess {

    private static final Logger log = LoggerFactory.getLogger(ExportFileAccess.class);
    private static final Set<String> DOWNLOADABLE_STATUSES = Set.of("export_v1", "stub_written", "export_failed");
    /** Max attachment UUIDs per {@link DownloadPart#BINARIES} request. */
    public static final int MAX_BINARIES_FILE_IDS = 32;

    private final Optional<Path> exportDirRoot;
    private final Optional<MinioClient> minioClient;
    private final String minioBucket;

    public ExportFileAccess(AppConfig appConfig) {
        this(appConfig, Optional.empty());
    }

    public ExportFileAccess(AppConfig appConfig, Optional<MinioClient> minioClient) {
        this.exportDirRoot = appConfig.exportDir();
        this.minioClient = minioClient;
        this.minioBucket = appConfig.minioBucket();
    }

    public boolean isConfigured() {
        return exportDirRoot.isPresent() || minioClient.isPresent();
    }

    public boolean isDownloadableStatus(String status) {
        return status != null && DOWNLOADABLE_STATUSES.contains(status);
    }

    public boolean canDownload(ExportJobRow job) {
        return canDownloadPart(job, DownloadPart.BUNDLE);
    }

    public boolean canDownloadPart(ExportJobRow job, DownloadPart part) {
        if (job == null) {
            return false;
        }
        if ((part == DownloadPart.MANIFEST || part == DownloadPart.BINARY || part == DownloadPart.BINARIES)
            && !ExportOutputRef.isZipBundlePath(job.outputPath())) {
            return false;
        }
        var minioKey = ExportOutputRef.parseMinioObjectKey(job.outputPath());
        if (minioKey.isPresent() && minioClient.isPresent()) {
            return true;
        }
        return resolveLocalFile(job).isPresent();
    }

    /**
     * Streams export bytes to {@code consumer}. Caller must read the stream inside {@code consumer}.
     */
    public boolean streamJobContent(ExportJobRow job, Consumer<InputStream> consumer) throws IOException {
        if (job == null) {
            return false;
        }
        var minioKey = ExportOutputRef.parseMinioObjectKey(job.outputPath());
        if (minioKey.isPresent() && minioClient.isPresent()) {
            try (var in = minioClient.get().getObject(GetObjectArgs.builder()
                .bucket(minioBucket)
                .object(minioKey.get())
                .build())) {
                consumer.accept(in);
                return true;
            } catch (Exception e) {
                log.warn("MinIO export download failed jobId={} key={}: {}", job.id(), minioKey.get(), e.getMessage());
                return false;
            }
        }
        var file = resolveLocalFile(job);
        if (file.isEmpty()) {
            return false;
        }
        try (var in = Files.newInputStream(file.get())) {
            consumer.accept(in);
        }
        return true;
    }

    Optional<Path> resolveReadableFile(ExportJobRow job) {
        return resolveLocalFile(job);
    }

    static String safeExportFileName(String jobId) {
        return ExportOutputRef.safeJobIdForFilename(jobId) + ".export.json";
    }

    public static String safeDownloadFileName(ExportJobRow job) {
        if (job == null) {
            return "export.dat";
        }
        return ExportOutputRef.downloadFileName(job.id().toString(), job.outputPath());
    }

    public static String downloadMediaType(ExportJobRow job) {
        return downloadMediaType(job, DownloadPart.BUNDLE);
    }

    public enum DownloadPart {
        BUNDLE,
        JSON,
        MANIFEST,
        BINARY,
        BINARIES;

        public static DownloadPart parse(String raw) {
            if (raw == null || raw.isBlank() || "bundle".equalsIgnoreCase(raw.trim())) {
                return BUNDLE;
            }
            return switch (raw.trim().toLowerCase()) {
                case "json" -> JSON;
                case "manifest" -> MANIFEST;
                case "binary" -> BINARY;
                case "binaries" -> BINARIES;
                default -> throw new IllegalArgumentException(raw);
            };
        }
    }

    public record BinariesResolution(
        List<ExportZipManifestReader.AttachmentRef> attachments,
        List<UUID> missingFileIds
    ) {
        public boolean complete() {
            return missingFileIds.isEmpty();
        }
    }

    public record DownloadTarget(String fileName, String mediaType) {
    }

    public static DownloadTarget downloadTarget(ExportJobRow job, DownloadPart part) {
        return downloadTarget(job, part, null);
    }

    public static DownloadTarget downloadTarget(
        ExportJobRow job,
        DownloadPart part,
        ExportZipManifestReader.AttachmentRef binaryRef
    ) {
        if (job == null) {
            return new DownloadTarget("export.dat", MediaType.APPLICATION_OCTET_STREAM);
        }
        return switch (part) {
            case BUNDLE -> new DownloadTarget(
                safeDownloadFileName(job),
                ExportOutputRef.isZipBundlePath(job.outputPath()) ? "application/zip" : MediaType.APPLICATION_JSON
            );
            case JSON -> new DownloadTarget(
                safeExportFileName(job.id().toString()),
                MediaType.APPLICATION_JSON
            );
            case MANIFEST -> new DownloadTarget(
                ExportOutputRef.safeJobIdForFilename(job.id().toString()) + ".attachments-manifest.json",
                MediaType.APPLICATION_JSON
            );
            case BINARY -> {
                if (binaryRef == null) {
                    yield new DownloadTarget("attachment.dat", MediaType.APPLICATION_OCTET_STREAM);
                }
                var mime = binaryRef.mediaType();
                if (mime == null || mime.isBlank()) {
                    mime = MediaType.APPLICATION_OCTET_STREAM;
                }
                yield new DownloadTarget(binaryRef.downloadFileName(), mime);
            }
            case BINARIES -> new DownloadTarget(
                ExportOutputRef.safeJobIdForFilename(job.id().toString()) + ".attachments-selection.zip",
                "application/zip"
            );
        };
    }

    public Optional<ExportZipManifestReader.AttachmentRef> resolveBinaryAttachment(ExportJobRow job, UUID fileId)
        throws IOException {
        if (job == null || fileId == null) {
            return Optional.empty();
        }
        var index = loadManifestIndex(job);
        return index.map(m -> m.get(fileId.toString()));
    }

    public Optional<Map<String, ExportZipManifestReader.AttachmentRef>> loadManifestIndex(ExportJobRow job)
        throws IOException {
        return loadManifestEntries(job).map(entries -> {
            var index = new LinkedHashMap<String, ExportZipManifestReader.AttachmentRef>();
            for (var entry : entries.values()) {
                var downloadName = entry.filename() != null && !entry.filename().isBlank()
                    ? entry.filename()
                    : entry.fileId();
                index.put(entry.fileId(), new ExportZipManifestReader.AttachmentRef(
                    entry.zipPath(), downloadName, entry.mimeType()));
            }
            return index;
        });
    }

    public Optional<Map<String, ExportZipManifestReader.ManifestEntry>> loadManifestEntries(ExportJobRow job)
        throws IOException {
        if (job == null || !ExportOutputRef.isZipBundlePath(job.outputPath())) {
            return Optional.empty();
        }
        var index = new AtomicReference<Map<String, ExportZipManifestReader.ManifestEntry>>();
        var found = streamDownloadPart(job, DownloadPart.MANIFEST, in -> {
            try {
                index.set(ExportZipManifestReader.indexManifestEntries(in));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (!found || index.get() == null) {
            return Optional.empty();
        }
        return Optional.of(index.get());
    }

    public ExportAttachmentsListResponse listAttachmentManifest(ExportJobRow job) throws IOException {
        return listAttachmentManifest(job, 0, 0);
    }

    public ExportAttachmentsListResponse listAttachmentManifest(ExportJobRow job, int offset, int limit)
        throws IOException {
        if (job == null || !ExportOutputRef.isZipBundlePath(job.outputPath())) {
            return new ExportAttachmentsListResponse(false, 0, 0, 0, 0, List.of());
        }
        var entries = loadManifestEntries(job).orElse(Map.of());
        var items = entries.values().stream()
            .sorted(java.util.Comparator.comparing(ExportZipManifestReader.ManifestEntry::fileId))
            .map(e -> new ExportAttachmentsListResponse.ExportAttachmentListItem(
                e.fileId(),
                e.filename(),
                e.mimeType(),
                e.sizeBytes(),
                e.sha256(),
                e.zipPath()))
            .toList();
        var total = items.size();
        var safeOffset = ExportJobReadSupport.normalizeOffset(offset);
        var pageLimit = ExportJobReadSupport.normalizeLimit(limit);
        List<ExportAttachmentsListResponse.ExportAttachmentListItem> page;
        int effectiveLimit;
        if (pageLimit <= 0) {
            page = items.subList(Math.min(safeOffset, total), total);
            effectiveLimit = 0;
        } else {
            var end = Math.min(safeOffset + pageLimit, total);
            page = items.subList(Math.min(safeOffset, total), end);
            effectiveLimit = pageLimit;
        }
        return new ExportAttachmentsListResponse(true, total, page.size(), safeOffset, effectiveLimit, page);
    }

    public BinariesResolution resolveBinaries(ExportJobRow job, List<UUID> fileIds) throws IOException {
        if (job == null || fileIds == null || fileIds.isEmpty()) {
            return new BinariesResolution(List.of(), List.copyOf(fileIds != null ? fileIds : List.of()));
        }
        var index = loadManifestIndex(job).orElse(Map.of());
        var seen = new LinkedHashMap<UUID, ExportZipManifestReader.AttachmentRef>();
        var missing = new ArrayList<UUID>();
        for (var id : fileIds) {
            if (id == null) {
                continue;
            }
            var ref = index.get(id.toString());
            if (ref == null) {
                missing.add(id);
            } else {
                seen.putIfAbsent(id, ref);
            }
        }
        return new BinariesResolution(List.copyOf(seen.values()), List.copyOf(missing));
    }

    public void streamBinariesZip(ExportJobRow job, List<ExportZipManifestReader.AttachmentRef> attachments, OutputStream out)
        throws IOException {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        try (var zos = new ZipOutputStream(out)) {
            for (var ref : attachments) {
                zos.putNextEntry(new ZipEntry(ref.zipPath()));
                var copied = streamZipEntry(job, ref.zipPath(), in -> {
                    try {
                        in.transferTo(zos);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                zos.closeEntry();
                if (!copied) {
                    throw new IOException("zip entry missing in export bundle: " + ref.zipPath());
                }
            }
        }
    }

    public static String downloadMediaType(ExportJobRow job, DownloadPart part) {
        return downloadTarget(job, part).mediaType();
    }

    /**
     * {@link DownloadPart#BUNDLE} — whole artifact; {@link DownloadPart#JSON} — {@code export.json} from zip or plain json;
     * {@link DownloadPart#MANIFEST} — {@code attachments/manifest.json} (zip only).
     */
    public boolean streamDownloadPart(ExportJobRow job, DownloadPart part, Consumer<InputStream> consumer)
        throws IOException {
        return streamDownloadPart(job, part, null, consumer);
    }

    public boolean streamDownloadPart(
        ExportJobRow job,
        DownloadPart part,
        UUID fileId,
        Consumer<InputStream> consumer
    ) throws IOException {
        if (job == null) {
            return false;
        }
        if (part == DownloadPart.BINARY) {
            return false;
        }
        if (part == DownloadPart.BUNDLE) {
            return streamJobContent(job, consumer);
        }
        if (!ExportOutputRef.isZipBundlePath(job.outputPath())) {
            return part == DownloadPart.JSON && streamJobContent(job, consumer);
        }
        var entry = part == DownloadPart.JSON
            ? ExportOutputRef.ZIP_JSON_ENTRY
            : ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST;
        return streamZipEntry(job, entry, consumer);
    }

    public boolean streamAttachmentEntry(ExportJobRow job, String zipEntryPath, Consumer<InputStream> consumer)
        throws IOException {
        if (zipEntryPath == null || zipEntryPath.isBlank() || zipEntryPath.contains("..")) {
            return false;
        }
        return streamZipEntry(job, zipEntryPath, consumer);
    }

    private boolean streamZipEntry(ExportJobRow job, String entryName, Consumer<InputStream> consumer)
        throws IOException {
        var minioKey = ExportOutputRef.parseMinioObjectKey(job.outputPath());
        if (minioKey.isPresent() && minioClient.isPresent()) {
            try (var zipIn = minioClient.get().getObject(GetObjectArgs.builder()
                .bucket(minioBucket)
                .object(minioKey.get())
                .build())) {
                return ExportZipEntryReader.streamEntry(zipIn, entryName, consumer);
            } catch (Exception e) {
                log.warn("MinIO zip entry {} failed jobId={}: {}", entryName, job.id(), e.getMessage());
                return false;
            }
        }
        var file = resolveLocalFile(job);
        if (file.isEmpty()) {
            return false;
        }
        try (var zipIn = Files.newInputStream(file.get())) {
            return ExportZipEntryReader.streamEntry(zipIn, entryName, consumer);
        }
    }

    private Optional<Path> resolveLocalFile(ExportJobRow job) {
        var root = exportDirRoot.orElse(null);
        if (root == null || job == null) {
            return Optional.empty();
        }
        var rootNorm = root.toAbsolutePath().normalize();
        var downloadName = safeDownloadFileName(job);
        var expected = rootNorm.resolve(downloadName).normalize();
        if (!expected.startsWith(rootNorm)) {
            return Optional.empty();
        }
        if (Files.isRegularFile(expected)) {
            return Optional.of(expected);
        }

        var jsonFallback = rootNorm.resolve(safeExportFileName(job.id().toString())).normalize();
        if (jsonFallback.startsWith(rootNorm) && Files.isRegularFile(jsonFallback)) {
            return Optional.of(jsonFallback);
        }

        var stored = job.outputPath();
        if (stored != null && !stored.isBlank() && !stored.startsWith(ExportOutputRef.MINIO_PREFIX)) {
            var fromStored = Path.of(stored).normalize();
            if (fromStored.isAbsolute() && fromStored.startsWith(rootNorm) && Files.isRegularFile(fromStored)) {
                return Optional.of(fromStored);
            }
            var fileName = fromStored.getFileName();
            if (fileName != null) {
                var byName = rootNorm.resolve(fileName).normalize();
                if (byName.startsWith(rootNorm) && Files.isRegularFile(byName)) {
                    return Optional.of(byName);
                }
            }
        }
        if (Files.isRegularFile(expected)) {
            return Optional.of(expected);
        }
        return Optional.empty();
    }

    static void transferToOutput(InputStream in, java.io.OutputStream out) {
        try {
            in.transferTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
