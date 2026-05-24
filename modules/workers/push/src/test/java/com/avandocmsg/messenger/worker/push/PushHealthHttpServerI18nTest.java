package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushHealthHttpServerI18nTest {

    @Test
    void healthOkBodyIsLocalizedEnglish() throws Exception {
        var messages = commonMessages(Locale.ENGLISH);
        try (var server = PushHealthHttpServer.start(0, (PushReadinessCheck) () -> true, messages)) {
            var body = getHealthBody(server.getPort());
            assertEquals(200, body.status());
            assertEquals("healthy", body.text().trim());
        }
    }

    @Test
    void healthOkBodyIsLocalizedRussian() throws Exception {
        var messages = commonMessages(Locale.forLanguageTag("ru"));
        try (var server = PushHealthHttpServer.start(0, (PushReadinessCheck) () -> true, messages)) {
            var body = getHealthBody(server.getPort());
            assertEquals(200, body.status());
            assertEquals("ok", body.text().trim());
        }
    }

    private static UserMessageSource commonMessages(Locale locale) {
        return new CompositeMessageSource(
            locale,
            PushHealthHttpServerI18nTest.class.getClassLoader(),
            List.of("com.avandocmsg.messenger.i18n.messages_common"));
    }

    private static HealthResponse getHealthBody(int port) throws Exception {
        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build();
        var res = client.send(req, HttpResponse.BodyHandlers.ofString());
        return new HealthResponse(res.statusCode(), res.body());
    }

    private record HealthResponse(int status, String text) {
    }
}
