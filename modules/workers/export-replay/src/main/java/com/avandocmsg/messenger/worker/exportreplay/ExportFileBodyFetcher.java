package com.avandocmsg.messenger.worker.exportreplay;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;

import java.io.InputStream;
import java.util.Optional;

/** Downloads user file blobs from MinIO ({@code {fileId}/{filename}} object layout). */
interface ExportFileBodyFetcher {

  record OpenResult(InputStream stream, long sizeBytes, String contentType) {}

  Optional<OpenResult> open(String fileId, String filename, long maxBytes);

  final class Minio implements ExportFileBodyFetcher {

    private final MinioClient client;
    private final String bucket;

    Minio(MinioClient client, String bucket) {
      this.client = client;
      this.bucket = bucket;
    }

    @Override
    public Optional<OpenResult> open(String fileId, String filename, long maxBytes) {
      if (fileId == null || fileId.isBlank()) {
        return Optional.empty();
      }
      var objectName = fileId + "/" + (filename != null && !filename.isBlank() ? filename : "file");
      try {
        var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
        var size = stat.size();
        if (size < 0 || size > maxBytes) {
          return Optional.empty();
        }
        var in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        var ct = stat.contentType();
        return Optional.of(new OpenResult(in, size, ct != null ? ct : "application/octet-stream"));
      } catch (ErrorResponseException e) {
        if ("NoSuchKey".equals(e.errorResponse().code())) {
          return Optional.empty();
        }
        return Optional.empty();
      } catch (Exception e) {
        return Optional.empty();
      }
    }

    static Minio fromEnv() {
      var endpoint = System.getenv("MINIO_ENDPOINT");
      var accessKey = System.getenv("MINIO_ACCESS_KEY");
      var secretKey = System.getenv("MINIO_SECRET_KEY");
      var bucket = System.getenv().getOrDefault("MINIO_BUCKET", "avandocmsg");
      if (endpoint == null || endpoint.isBlank() || accessKey == null || secretKey == null) {
        return null;
      }
      var client = MinioClient.builder()
          .endpoint(endpoint)
          .credentials(accessKey, secretKey)
          .build();
      return new Minio(client, bucket);
    }
  }
}
