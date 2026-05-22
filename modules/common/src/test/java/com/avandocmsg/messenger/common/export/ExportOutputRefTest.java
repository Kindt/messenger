package com.avandocmsg.messenger.common.export;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportOutputRefTest {

    @Test
    void objectKey_andParseRoundTrip() {
        var jobId = UUID.randomUUID().toString();
        var key = ExportOutputRef.objectKey(jobId);
        var stored = ExportOutputRef.minioStoredPath(key);
        assertEquals(key, ExportOutputRef.parseMinioObjectKey(stored).orElseThrow());
        assertEquals("minio", ExportOutputRef.outputStorage(stored));
    }

    @Test
    void parseRejectsTraversal() {
        assertTrue(ExportOutputRef.parseMinioObjectKey("minio:exports/../x").isEmpty());
    }

    @Test
    void outputFormat_zipOrJson() {
        var jobId = UUID.randomUUID().toString();
        assertEquals("json", ExportOutputRef.outputFormat("/data/x.export.json"));
        assertEquals("zip", ExportOutputRef.outputFormat(ExportOutputRef.minioStoredPath(ExportOutputRef.zipObjectKey(jobId))));
    }

    @Test
    void zipObjectKey_andDownloadFileName() {
        var jobId = UUID.randomUUID().toString();
        var stored = ExportOutputRef.minioStoredPath(ExportOutputRef.zipObjectKey(jobId));
        assertTrue(ExportOutputRef.isZipBundlePath(stored));
        assertEquals(ExportOutputRef.safeJobIdForFilename(jobId) + ".export.zip",
            ExportOutputRef.downloadFileName(jobId, stored));
    }
}
