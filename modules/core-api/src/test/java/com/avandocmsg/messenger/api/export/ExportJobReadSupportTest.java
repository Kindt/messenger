package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.repository.ExportJobRepository.ExportJobRow;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportJobReadSupportTest {

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  void attachmentsResponse_paginates() throws Exception {
    var jobId = UUID.randomUUID();
    var zipPath = tempDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
    try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
      zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
      var sb = new StringBuilder("{\"files\":[");
      for (int i = 0; i < 5; i++) {
        if (i > 0) {
          sb.append(',');
        }
        var id = UUID.randomUUID();
        sb.append("{\"fileId\":\"").append(id)
            .append("\",\"filename\":\"f").append(i)
            .append(".txt\",\"mimeType\":\"text/plain\",\"zipPath\":\"attachments/")
            .append(id).append("/f\",\"sizeBytes\":1,\"sha256\":\"\"}");
      }
      sb.append("]}");
      zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    var access = ExportFileAccessTest.accessWithDir(tempDir);
    var now = Instant.now();
    var row = new ExportJobRow(
        jobId, UUID.randomUUID(), UUID.randomUUID(), "export_v1",
        zipPath.toString(), true, now, now, now);
    var res = ExportJobReadSupport.attachmentsResponse(row, access, I18nTestFixtures.messagesEn(), 1, 2);
    assertEquals(200, res.getStatus());
    var body = (ExportAttachmentsListResponse) res.getEntity();
    assertEquals(5, body.totalCount());
    assertEquals(2, body.fileCount());
    assertEquals(1, body.offset());
    assertEquals(2, body.limit());
  }
}
