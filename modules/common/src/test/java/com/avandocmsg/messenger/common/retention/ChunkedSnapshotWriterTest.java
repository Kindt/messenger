package com.avandocmsg.messenger.common.retention;

import com.avandocmsg.messenger.common.dto.DeepArchiveManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedSnapshotWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PutCapableS3TestServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void writeChunkedSnapshot_writesPartsAndManifest() throws Exception {
        String bucket = "bucket";
        String prefix = "retention/msg-1/";
        byte[] payload = "{\"content\":\"hello world\"}".getBytes(StandardCharsets.UTF_8);
        int chunkSize = 8;

        server = new PutCapableS3TestServer();
        var client = minioClient(server.port());

        int parts = ChunkedSnapshotWriter.writeChunkedSnapshot(
            client, bucket, prefix, "msg-1", payload, chunkSize, MAPPER);

        assertEquals(4, parts);
        assertTrue(server.objects().containsKey("/" + bucket + "/" + prefix + "part-000.json"));
        assertTrue(server.objects().containsKey("/" + bucket + "/" + prefix + "part-001.json"));
        assertTrue(server.objects().containsKey("/" + bucket + "/" + prefix + "part-002.json"));
        assertTrue(server.objects().containsKey("/" + bucket + "/" + prefix + "part-003.json"));
        assertTrue(server.objects().containsKey("/" + bucket + "/" + prefix + ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME));

        var manifestBytes = server.objects().get("/" + bucket + "/" + prefix + ArchiveSnapshotFormat.CHUNK_MANIFEST_FILENAME);
        var manifest = MAPPER.readValue(manifestBytes, DeepArchiveManifest.class);
        assertEquals("msg-1", manifest.messageId());
        assertEquals(4, manifest.chunkCount());
        assertEquals(payload.length, manifest.totalSizeBytes());

        var reassembled = new ByteArrayOutputStream();
        for (int i = 0; i < parts; i++) {
            var key = "/" + bucket + "/" + prefix + String.format(ArchiveSnapshotFormat.CHUNK_PART_FORMAT, i);
            reassembled.writeBytes(server.objects().get(key));
        }
        assertArrayEquals(payload, reassembled.toByteArray());
    }

    private static MinioClient minioClient(int port) {
        return MinioClient.builder()
            .endpoint("http://127.0.0.1:" + port)
            .credentials("x", "y")
            .build();
    }

    private static final class PutCapableS3TestServer implements AutoCloseable {
        private final HttpServer http;
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        private PutCapableS3TestServer() throws IOException {
            this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.http.createContext("/", this::handle);
            this.http.start();
        }

        private int port() {
            return http.getAddress().getPort();
        }

        private Map<String, byte[]> objects() {
            return Map.copyOf(objects);
        }

        private void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String query = ex.getRequestURI().getRawQuery();
            if ("GET".equalsIgnoreCase(method) && query != null && query.contains("location")) {
                byte[] location = "<LocationConstraint>us-east-1</LocationConstraint>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/xml");
                ex.sendResponseHeaders(200, location.length);
                ex.getResponseBody().write(location);
                ex.close();
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                var body = ex.getRequestBody().readAllBytes();
                objects.put(path, body);
                ex.getResponseHeaders().add("ETag", "\"test-etag\"");
                ex.sendResponseHeaders(200, -1);
                ex.close();
                return;
            }
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                byte[] body = objects.get(path);
                if (body == null) {
                    if ("HEAD".equalsIgnoreCase(method)) {
                        ex.sendResponseHeaders(404, -1);
                    } else {
                        ex.sendResponseHeaders(404, -1);
                    }
                    ex.close();
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(method) ? -1 : body.length);
                if (!"HEAD".equalsIgnoreCase(method)) {
                    ex.getResponseBody().write(body);
                }
                ex.close();
                return;
            }
            ex.sendResponseHeaders(405, -1);
            ex.close();
        }

        @Override
        public void close() {
            http.stop(0);
        }
    }
}
