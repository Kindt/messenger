package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportArtifactWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void writePrettyJson_roundTripsObjectNode() throws Exception {
        var root = MAPPER.createObjectNode()
            .put("jobId", "job-1")
            .put("messageCount", 2);
        var messages = root.putArray("messages");
        messages.addObject().put("id", "m1");
        messages.addObject().put("id", "m2");

        var out = tempDir.resolve("job.export.json");
        ExportArtifactWriter.writePrettyJson(out, root);

        var parsed = MAPPER.readTree(out.toFile());
        assertEquals("job-1", parsed.path("jobId").asText());
        assertEquals(2, parsed.path("messageCount").asInt());
        assertEquals(2, parsed.path("messages").size());
    }

    @Test
    void writePrettyJsonZipEntry_includesExportJson() throws Exception {
        var root = MAPPER.createObjectNode().put("exportStatus", "ok");
        var zip = tempDir.resolve("job.export.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            ExportArtifactWriter.writePrettyJsonZipEntry(zos, ExportOutputRef.ZIP_JSON_ENTRY, root);
        }

        try (var zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            var found = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (ExportOutputRef.ZIP_JSON_ENTRY.equals(entry.getName())) {
                    var parsed = MAPPER.readTree(zis.readAllBytes());
                    assertEquals("ok", parsed.path("exportStatus").asText());
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }
}
