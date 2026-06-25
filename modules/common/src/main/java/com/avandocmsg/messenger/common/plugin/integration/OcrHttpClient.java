package com.avandocmsg.messenger.common.plugin.integration;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.http.HttpClientSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** On-prem OCR HTTP service or mock fixture. */
public final class OcrHttpClient {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final HttpClient HTTP = HttpClientSupport.sharedClient();

    private OcrHttpClient() {}

    public record OcrFields(String vendor, String amount, String currency, String date, String invoiceNo) {}

    public static OcrFields extract(String fileId, Map<String, Object> policySnapshot) throws Exception {
        if (IntegrationEnv.useMock(ocrLiveConfigured()) || mustUseMockForPolicy(policySnapshot)) {
            return extractMock(fileId);
        }
        return extractLive(fileId);
    }

    private static boolean mustUseMockForPolicy(Map<String, Object> policySnapshot) {
        if (!IntegrationEnv.ocrOnPremOnly(policySnapshot)) {
            return false;
        }
        var url = IntegrationEnv.getenv("OCR_HTTP_URL");
        if (url.isBlank()) {
            return true;
        }
        return url.contains(".openai.") || url.contains("azure.com/cognitive");
    }

    public static OcrFields extractMock(String fileId) throws Exception {
        var id = fileId == null || fileId.isBlank() ? "invoice-demo.pdf" : fileId;
        var url = IntegrationEnv.mockApiBase() + "/ocr/v1/extract.json?file_id="
            + java.net.URLEncoder.encode(id, StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OCR mock HTTP " + response.statusCode());
        }
        return parseFields(MAPPER.readTree(response.body()).path("fields"));
    }

    private static OcrFields extractLive(String fileId) throws Exception {
        var base = IntegrationEnv.trimSlash(IntegrationEnv.getenv("OCR_HTTP_URL"));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("file_id", fileId != null ? fileId : "invoice-demo.pdf");
        var request = HttpRequest.newBuilder()
            .uri(URI.create(base + "/v1/extract"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OCR HTTP " + response.statusCode());
        }
        return parseFields(MAPPER.readTree(response.body()).path("fields"));
    }

    private static boolean ocrLiveConfigured() {
        return IntegrationEnv.isSet("OCR_HTTP_URL");
    }

    private static OcrFields parseFields(JsonNode fields) {
        return new OcrFields(
            fields.path("vendor").asText("?"),
            fields.path("amount").asText("?"),
            fields.path("currency").asText("RUB"),
            fields.path("date").asText("?"),
            fields.path("invoice_no").asText(fields.path("invoiceNo").asText("?"))
        );
    }
}
