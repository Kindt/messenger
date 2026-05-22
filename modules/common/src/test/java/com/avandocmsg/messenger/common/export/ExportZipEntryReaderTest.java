package com.avandocmsg.messenger.common.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportZipEntryReaderTest {

    @Test
    void streamEntry_readsNamedEntry() throws Exception {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("a.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_JSON_ENTRY));
            zos.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var out = new ByteArrayOutputStream();
        var found = ExportZipEntryReader.streamEntry(
            new ByteArrayInputStream(baos.toByteArray()),
            ExportOutputRef.ZIP_JSON_ENTRY,
            in -> {
                try {
                    in.transferTo(out);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        );
        assertTrue(found);
        assertArrayEquals("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    void streamEntry_missingReturnsFalse() throws Exception {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("only.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertFalse(ExportZipEntryReader.streamEntry(
            new ByteArrayInputStream(baos.toByteArray()),
            ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST,
            in -> {}
        ));
    }
}
