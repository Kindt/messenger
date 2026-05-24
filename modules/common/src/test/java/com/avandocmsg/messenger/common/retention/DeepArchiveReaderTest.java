package com.avandocmsg.messenger.common.retention;

import com.avandocmsg.messenger.common.dto.ChunkEntry;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepArchiveReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private S3LikeTestServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void readChunkedMessage_reassemblesParts() throws Exception {
        String bucket = "bucket";
        String messageId = "m1";
        byte[] part0 = "{\"a\":".getBytes(StandardCharsets.UTF_8);
        byte[] part1 = "\"b\"}".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "{\"a\":\"b\"}".getBytes(StandardCharsets.UTF_8);
        var manifest = new DeepArchiveManifest(
            messageId,
            2,
            List.of(
                new ChunkEntry("part-000.json", 0, part0.length, "x"),
                new ChunkEntry("part-001.json", 1, part1.length, "y")
            ),
            expected.length,
            "z"
        );
        byte[] manifestBytes = MAPPER.writeValueAsBytes(manifest);

        server = new S3LikeTestServer(Map.ofEntries(
            Map.entry("/" + bucket + "/messages/" + messageId + "/manifest.json", manifestBytes),
            Map.entry("/" + bucket + "/messages/" + messageId + "/part-000.json", part0),
            Map.entry("/" + bucket + "/messages/" + messageId + "/part-001.json", part1),
            Map.entry("/messages/" + messageId + "/manifest.json", manifestBytes),
            Map.entry("/messages/" + messageId + "/part-000.json", part0),
            Map.entry("/messages/" + messageId + "/part-001.json", part1)
        ));
        var client = minioClient(server.port());

        Optional<java.io.InputStream> stream = DeepArchiveReader.readMessage(client, bucket, messageId);
        assertTrue(stream.isPresent(), "requested=" + server.requests());
        try (var in = stream.get()) {
            assertArrayEquals(expected, readAll(in));
        }
    }

    @Test
    void readMessage_fallsBackToFlatObject_whenManifestMissing() throws Exception {
        String bucket = "bucket";
        String messageId = "m2";
        byte[] flat = "{\"legacy\":true}".getBytes(StandardCharsets.UTF_8);
        server = new S3LikeTestServer(Map.ofEntries(
            Map.entry("/" + bucket + "/messages/" + messageId + ".json", flat),
            Map.entry("/messages/" + messageId + ".json", flat)
        ));
        var client = minioClient(server.port());

        Optional<java.io.InputStream> stream = DeepArchiveReader.readMessage(client, bucket, messageId);
        assertTrue(stream.isPresent(), "requested=" + server.requests());
        try (var in = stream.get()) {
            assertArrayEquals(flat, readAll(in));
        }
    }

    @Test
    void readMessage_returnsEmpty_whenNoChunkedOrFlatObject() throws Exception {
        server = new S3LikeTestServer(Map.of());
        var client = minioClient(server.port());
        assertFalse(DeepArchiveReader.readMessage(client, "bucket", "missing").isPresent());
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        var out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private static MinioClient minioClient(int port) {
        return MinioClient.builder()
            .endpoint("http://127.0.0.1:" + port)
            .credentials("x", "y")
            .build();
    }

    private static final class S3LikeTestServer implements AutoCloseable {
        private final HttpServer http;
        private final Map<String, byte[]> objects;
        private final CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();

        private S3LikeTestServer(Map<String, byte[]> initialObjects) throws IOException {
            this.objects = new ConcurrentHashMap<>(initialObjects);
            this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.http.createContext("/", this::handle);
            this.http.start();
        }

        private int port() {
            return http.getAddress().getPort();
        }

        private List<String> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String query = ex.getRequestURI().getRawQuery();
            requests.add(method + " " + path + (query != null ? "?" + query : ""));
            if ("GET".equalsIgnoreCase(method) && query != null && query.contains("location")) {
                byte[] location = "<LocationConstraint>us-east-1</LocationConstraint>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/xml");
                ex.sendResponseHeaders(200, location.length);
                ex.getResponseBody().write(location);
                ex.close();
                return;
            }
            byte[] body = objects.get(path);
            if (body == null) {
                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Error><Code>NoSuchKey</Code><Message>Not Found</Message></Error>";
                byte[] err = xml.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/xml");
                ex.sendResponseHeaders(404, "HEAD".equalsIgnoreCase(method) ? -1 : err.length);
                if (!"HEAD".equalsIgnoreCase(method)) {
                    ex.getResponseBody().write(err);
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
        }

        @Override
        public void close() {
            http.stop(0);
        }
    }
}
