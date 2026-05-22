package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.ExportJobRepository.ExportJobRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.avandocmsg.messenger.common.export.ExportOutputRef;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExportFileAccessTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void resolveReadableFile_byExpectedName() throws Exception {
        var jobId = UUID.randomUUID();
        var file = tempDir.resolve(ExportFileAccess.safeExportFileName(jobId.toString()));
        Files.writeString(file, "{}");
        var access = accessWithDir(tempDir);
        var row = row(jobId, UUID.randomUUID(), "export_v1", null);
        var resolved = access.resolveReadableFile(row);
        assertTrue(resolved.isPresent());
        assertEquals(file.toAbsolutePath().normalize(), resolved.get().toAbsolutePath().normalize());
    }

    @Test
    void resolveReadableFile_byOutputPathFileName() throws Exception {
        var jobId = UUID.randomUUID();
        var file = tempDir.resolve(ExportFileAccess.safeExportFileName(jobId.toString()));
        Files.writeString(file, "{}");
        var access = accessWithDir(tempDir);
        var row = row(jobId, UUID.randomUUID(), "export_v1", "/other/host/export/" + file.getFileName());
        assertTrue(access.resolveReadableFile(row).isPresent());
    }

    @Test
    void streamAttachmentEntry_readsFromZipManifest() throws Exception {
        var jobId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var zipPath = tempDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
        var entryPath = "attachments/" + fileId + "/doc.txt";
        var payload = "hello-attachment";
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            var manifest = """
                {"files":[{"fileId":"%s","filename":"doc.txt","mimeType":"text/plain","zipPath":"%s"}]}
                """.formatted(fileId, entryPath);
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(entryPath));
            zos.write(payload.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var access = accessWithDir(tempDir);
        var row = row(jobId, UUID.randomUUID(), "export_v1", "minio:ignored");
        row = new ExportJobRow(
            row.id(), row.chatId(), row.requestedBy(), row.status(),
            zipPath.toString(), row.messageTtlFilterApplied(),
            row.createdAt(), row.updatedAt(), row.completedAt());
        assertTrue(access.canDownloadPart(row, ExportFileAccess.DownloadPart.BINARY));
        var resolved = access.resolveBinaryAttachment(row, fileId);
        assertTrue(resolved.isPresent());
        assertEquals(entryPath, resolved.get().zipPath());
        var out = new ByteArrayOutputStream();
        assertTrue(access.streamAttachmentEntry(row, entryPath, in -> {
            try {
                in.transferTo(out);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        assertEquals(payload, out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void streamBinariesZip_buildsSelectionArchive() throws Exception {
        var jobId = UUID.randomUUID();
        var fileId1 = UUID.randomUUID();
        var fileId2 = UUID.randomUUID();
        var zipPath = tempDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
        var entry1 = "attachments/" + fileId1 + "/a.txt";
        var entry2 = "attachments/" + fileId2 + "/b.txt";
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            var manifest = """
                {"files":[
                {"fileId":"%s","filename":"a.txt","mimeType":"text/plain","zipPath":"%s"},
                {"fileId":"%s","filename":"b.txt","mimeType":"text/plain","zipPath":"%s"}
                ]}
                """.formatted(fileId1, entry1, fileId2, entry2);
            zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(entry1));
            zos.write("aaa".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(entry2));
            zos.write("bbb".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var access = accessWithDir(tempDir);
        var row = new ExportJobRow(
            jobId, UUID.randomUUID(), UUID.randomUUID(), "export_v1",
            zipPath.toString(), true, Instant.now(), Instant.now(), Instant.now());
        var resolution = access.resolveBinaries(row, java.util.List.of(fileId1, fileId2));
        assertTrue(resolution.complete());
        assertEquals(2, resolution.attachments().size());
        var out = new ByteArrayOutputStream();
        access.streamBinariesZip(row, resolution.attachments(), out);
        assertTrue(out.size() > 10);
    }

    @Test
    void listAttachmentManifest_returnsManifestRows() throws Exception {
        var jobId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var zipPath = tempDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            var manifest = """
                {"files":[{"fileId":"%s","filename":"doc.txt","mimeType":"text/plain","zipPath":"attachments/x","sizeBytes":9,"sha256":"deadbeef"}]}
                """.formatted(fileId);
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var access = accessWithDir(tempDir);
        var row = new ExportJobRow(
            jobId, UUID.randomUUID(), UUID.randomUUID(), "export_v1",
            zipPath.toString(), true, Instant.now(), Instant.now(), Instant.now());
        var list = access.listAttachmentManifest(row);
        assertTrue(list.zipBundle());
        assertEquals(1, list.totalCount());
        assertEquals(1, list.fileCount());
        assertEquals("deadbeef", list.files().getFirst().sha256());
        assertEquals(9, list.files().getFirst().sizeBytes());
    }

    @Test
    void notConfigured_whenExportDirMissing() {
        var access = new ExportFileAccess(new AppConfig());
        assertFalse(access.isConfigured());
    }

    static ExportFileAccess accessWithDir(java.nio.file.Path dir) {
        var config = new AppConfig() {
            @Override
            public java.util.Optional<java.nio.file.Path> exportDir() {
                return java.util.Optional.of(dir);
            }
        };
        return new ExportFileAccess(config);
    }

    private static ExportJobRow row(UUID jobId, UUID chatId, String status, String outputPath) {
        var now = Instant.now();
        return new ExportJobRow(jobId, chatId, UUID.randomUUID(), status, outputPath, true, now, now, now);
    }
}
