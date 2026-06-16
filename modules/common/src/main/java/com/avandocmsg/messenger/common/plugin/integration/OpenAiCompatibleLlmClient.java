package com.avandocmsg.messenger.common.plugin.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** OpenAI-compatible chat completion for L3 triage. */
public final class OpenAiCompatibleLlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private OpenAiCompatibleLlmClient() {}

    public record TriageResult(String category, String priority, String draftTitle) {}

    public static TriageResult triage(String text, Map<String, Object> policySnapshot) throws Exception {
        var onPremUrl = IntegrationEnv.trimSlash(IntegrationEnv.getenv("LLM_ON_PREM_URL"));
        var cloudUrl = IntegrationEnv.trimSlash(IntegrationEnv.getenv("LLM_BASE_URL"));
        var liveConfigured = !onPremUrl.isBlank() || !cloudUrl.isBlank();
        if (IntegrationEnv.useMock(liveConfigured)) {
            return triageMock(text);
        }
        var mode = IntegrationEnv.llmModeFromSnapshot(policySnapshot);
        if (!onPremUrl.isBlank()) {
            return triageLive(onPremUrl, text);
        }
        if ("on_prem_only".equals(mode) && isCloudHost(cloudUrl)) {
            return triageMock(text);
        }
        if (!cloudUrl.isBlank() && ("cloud_allowed".equals(mode) || "hybrid".equals(mode) || !isCloudHost(cloudUrl))) {
            return triageLive(cloudUrl, text);
        }
        return triageMock(text);
    }

    public static TriageResult triageMock(String text) throws Exception {
        var url = IntegrationEnv.mockApiBase() + "/ai/v1/triage.json";
        var body = MAPPER.createObjectNode().put("text", text).toString();
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI mock HTTP " + response.statusCode());
        }
        return parseTriage(MAPPER.readTree(response.body()));
    }

    private static TriageResult triageLive(String baseUrl, String text) throws Exception {
        var model = IntegrationEnv.getenv("LLM_MODEL");
        if (model.isBlank()) {
            model = "gpt-4o-mini";
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put("content", "Classify support thread. Reply JSON: category,priority,draft_title");
        messages.addObject().put("role", "user").put("content", text);
        var builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(root.toString()));
        var apiKey = IntegrationEnv.getenv("LLM_API_KEY");
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM HTTP " + response.statusCode());
        }
        var content = MAPPER.readTree(response.body())
            .path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return new TriageResult("general", "normal", text.substring(0, Math.min(80, text.length())));
        }
        try {
            return parseTriage(MAPPER.readTree(content));
        } catch (Exception ignored) {
            return new TriageResult("general", "normal", content.substring(0, Math.min(120, content.length())));
        }
    }

    private static boolean isCloudHost(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var lower = url.toLowerCase();
        return lower.contains("api.openai.com") || lower.contains("openai.azure.com")
            || lower.contains("anthropic.com") || lower.contains("googleapis.com");
    }

    private static TriageResult parseTriage(JsonNode json) {
        var draft = json.path("draft_ticket");
        var title = draft.path("title").asText(json.path("draft_title").asText("?"));
        return new TriageResult(
            json.path("category").asText("general"),
            json.path("priority").asText("normal"),
            title
        );
    }
}
