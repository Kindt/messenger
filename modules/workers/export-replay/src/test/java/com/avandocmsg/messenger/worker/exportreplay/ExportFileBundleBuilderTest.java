package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportFileBundleBuilderTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void build_includesJsonAndAttachment() throws Exception {
        var root = MAPPER.createObjectNode();
        var files = root.putArray("referencedFiles");
        var meta = files.addObject();
        meta.put("id", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        meta.put("filename", "doc.pdf");
        var zip = tempDir.resolve("job.export.zip");
        var fetcher = new ExportFileBodyFetcher() {
            @Override
            public Optional<OpenResult> open(String fileId, String filename, long maxBytes) {
                var bytes = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
                return Optional.of(new OpenResult(new ByteArrayInputStream(bytes), bytes.length, "application/pdf"));
            }
        };
        var stats = ExportFileBundleBuilder.build(root, zip, fetcher, 10, 1_000_000, WorkerMessageSources.forWorker(ExportReplayWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_export_replay"));
        assertEquals(1, stats.includedCount());
        assertTrue(Files.exists(zip));
        try (var zis = new ZipInputStream(Files.newInputStream(zip))) {
            var names = new java.util.ArrayList<String>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            assertTrue(names.contains(ExportOutputRef.ZIP_JSON_ENTRY));
            assertTrue(names.contains(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            assertTrue(names.stream().anyMatch(n -> n.startsWith(ExportFileBundleBuilder.ATTACHMENTS_PREFIX)));
        }
    }

    @Test
    void safeAttachmentFileName_stripsPath() {
        assertEquals("report_v2.pdf", ExportFileBundleBuilder.safeAttachmentFileName("../../../report v2.pdf"));
    }
}
