package com.avandocmsg.messenger.common.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportZipManifestReaderTest {

    @Test
    void indexByFileId_mapsAllEntries() throws Exception {
        var id1 = UUID.randomUUID().toString();
        var id2 = UUID.randomUUID().toString();
        var json = """
            {"files":[
              {"fileId":"%s","filename":"a.txt","mimeType":"text/plain","zipPath":"attachments/%s/a.txt"},
              {"fileId":"%s","filename":"b.pdf","mimeType":"application/pdf","zipPath":"attachments/%s/b.pdf"}
            ]}
            """.formatted(id1, id1, id2, id2);
        var index = ExportZipManifestReader.indexByFileId(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, index.size());
        assertEquals("a.txt", index.get(id1).downloadFileName());
        assertEquals("application/pdf", index.get(id2).mediaType());
    }

    @Test
    void indexManifestEntries_includesSizeAndHash() throws Exception {
        var fileId = UUID.randomUUID().toString();
        var json = """
            {"files":[{"fileId":"%s","filename":"a.bin","mimeType":"application/octet-stream","zipPath":"attachments/%s/a.bin","sizeBytes":42,"sha256":"abc123"}]}
            """.formatted(fileId, fileId);
        var index = ExportZipManifestReader.indexManifestEntries(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(42, index.get(fileId).sizeBytes());
        assertEquals("abc123", index.get(fileId).sha256());
    }

    @Test
    void findFile_returnsZipPathAndMime() throws Exception {
        var fileId = UUID.randomUUID().toString();
        var json = """
            {"files":[{"fileId":"%s","filename":"doc.pdf","mimeType":"application/pdf","zipPath":"attachments/%s/doc.pdf"}]}
            """.formatted(fileId, fileId);
        var ref = ExportZipManifestReader.findFile(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), fileId);
        assertTrue(ref.isPresent());
        assertEquals("attachments/" + fileId + "/doc.pdf", ref.get().zipPath());
        assertEquals("application/pdf", ref.get().mediaType());
        assertEquals("doc.pdf", ref.get().downloadFileName());
    }
}
