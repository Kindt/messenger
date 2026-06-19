package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.dto.ChunkEntry;
import com.avandocmsg.messenger.common.dto.DeepArchiveManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportReplayWorkerDeepArchiveTest {

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
    void fetchMessageSnapshot_readsChunkedDeepArchiveViaCommonReader() throws Exception {
        String bucket = "bucket";
        String messageId = "m-export";
        byte[] part0 = "{\"messageId\":\"m-export\",".getBytes(StandardCharsets.UTF_8);
        byte[] part1 = "\"source\":\"chunked\"}".getBytes(StandardCharsets.UTF_8);
        var manifest = new DeepArchiveManifest(
            messageId,
            2,
            List.of(
                new ChunkEntry("part-000.json", 0, part0.length, "x"),
                new ChunkEntry("part-001.json", 1, part1.length, "y")
            ),
            part0.length + part1.length,
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

        var client = MinioClient.builder()
            .endpoint("http://127.0.0.1:" + server.port())
            .credentials("x", "y")
            .build();
        var reader = new ExportDeepArchiveReader(client, bucket);

        var result = reader.fetchMessageSnapshot(messageId);
        assertTrue(result.isPresent());
        assertEquals("deep-archive", result.get().get("source").asText());
        assertEquals("messages/" + messageId + ".json", result.get().get("objectKey").asText());
        assertEquals("chunked", result.get().get("snapshot").get("source").asText());
    }

    private static final class S3LikeTestServer implements AutoCloseable {
        private final HttpServer http;
        private final Map<String, byte[]> objects;

        private S3LikeTestServer(Map<String, byte[]> initialObjects) throws IOException {
            this.objects = new ConcurrentHashMap<>(initialObjects);
            this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.http.createContext("/", this::handle);
            this.http.start();
        }

        private int port() {
            return http.getAddress().getPort();
        }

        private void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String query = ex.getRequestURI().getRawQuery();
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && query != null && query.contains("location")) {
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
                ex.sendResponseHeaders(404, err.length);
                ex.getResponseBody().write(err);
                ex.close();
                return;
            }
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        }

        @Override
        public void close() {
            http.stop(0);
        }
    }
}
