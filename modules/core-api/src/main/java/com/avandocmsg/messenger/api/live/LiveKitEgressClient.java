package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Minimal LiveKit egress HTTP client (RoomComposite → MinIO). */
public final class LiveKitEgressClient {

    private static final Logger log = LoggerFactory.getLogger(LiveKitEgressClient.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String FIELD_EGRESS_ID = "egressId";

    private final AppConfig appConfig;
    private final LiveKitTokenService tokenService;
    private final HttpClient httpClient;

    public LiveKitEgressClient(AppConfig appConfig, LiveKitTokenService tokenService) {
        this.appConfig = appConfig;
        this.tokenService = tokenService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean enabled() {
        return tokenService.enabled() && !appConfig.livekitEgressUrl().isBlank();
    }

    public Optional<String> startRoomComposite(String roomName, String filepath) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            var s3 = new LinkedHashMap<String, Object>();
            s3.put("accessKey", appConfig.minioAccessKey());
            s3.put("secret", appConfig.minioSecretKey());
            s3.put("bucket", appConfig.minioBucket());
            s3.put("endpoint", appConfig.minioEndpoint());
            s3.put("forcePathStyle", true);

            var file = new LinkedHashMap<String, Object>();
            file.put("filepath", filepath);
            file.put("s3", s3);

            var body = new LinkedHashMap<String, Object>();
            body.put("roomName", roomName);
            body.put("layout", "grid");
            body.put("file", file);

            var token = tokenService.createRoomRecordToken(roomName, 7200);
            var url = appConfig.livekitEgressUrl().replaceAll("/$", "")
                + "/twirp/livekit.Egress/StartRoomCompositeEgress";
            var resp = httpClient.send(jsonPost(url, token, body), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("LiveKit egress start failed HTTP {}: {}", resp.statusCode(), resp.body());
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(resp.body());
            var egressId = textOrNull(root, FIELD_EGRESS_ID);
            if (egressId == null) {
                egressId = textOrNull(root, "egress_id");
            }
            return egressId != null && !egressId.isBlank() ? Optional.of(egressId) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LiveKit egress start interrupted for room {}: {}", roomName, e.toString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("LiveKit egress start failed for room {}: {}", roomName, e.toString());
            return Optional.empty();
        }
    }

    public void stopEgress(String egressId) {
        if (!enabled() || egressId == null || egressId.isBlank()) {
            return;
        }
        try {
            var body = Map.of(FIELD_EGRESS_ID, egressId);
            var token = tokenService.createRoomRecordToken("*", 300);
            var url = appConfig.livekitEgressUrl().replaceAll("/$", "")
                + "/twirp/livekit.Egress/StopEgress";
            httpClient.send(jsonPost(url, token, body), HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LiveKit egress stop interrupted for {}: {}", egressId, e.toString());
        } catch (Exception e) {
            log.warn("LiveKit egress stop failed for {}: {}", egressId, e.toString());
        }
    }

    public Optional<EgressInfo> getEgress(String egressId) {
        if (!enabled() || egressId == null || egressId.isBlank()) {
            return Optional.empty();
        }
        try {
            var body = Map.of(FIELD_EGRESS_ID, egressId);
            var token = tokenService.createRoomRecordToken("*", 300);
            var url = appConfig.livekitEgressUrl().replaceAll("/$", "")
                + "/twirp/livekit.Egress/GetEgress";
            var resp = httpClient.send(jsonPost(url, token, body), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(resp.body());
            var info = root.has("info") ? root.get("info") : root;
            var status = textOrNull(info, "status");
            var filepath = extractFilepath(info);
            return Optional.of(new EgressInfo(egressId, status, filepath));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("LiveKit getEgress interrupted for {}: {}", egressId, e.toString());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("LiveKit getEgress failed for {}: {}", egressId, e.toString());
            return Optional.empty();
        }
    }

    private HttpRequest jsonPost(String url, String token, Object body) throws Exception {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
            .header(HEADER_CONTENT_TYPE, APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
    }

    private static String extractFilepath(JsonNode info) {
        if (info == null) {
            return null;
        }
        var file = info.get("file");
        if (file != null) {
            var fp = textOrNull(file, "filename");
            if (fp != null) {
                return fp;
            }
            fp = textOrNull(file, "filepath");
            if (fp != null) {
                return fp;
            }
        }
        var fileResults = info.get("fileResults");
        if (fileResults != null && fileResults.isArray() && !fileResults.isEmpty()) {
            return textOrNull(fileResults.get(0), "filename");
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    public record EgressInfo(String egressId, String status, String filepath) {
        public boolean completed() {
            return status != null && (status.contains("COMPLETE") || "EGRESS_COMPLETE".equalsIgnoreCase(status));
        }
    }
}
