package com.avandocmsg.messenger.common.http;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerHealthText;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Shared readiness body for worker {@code GET /health} handlers. */
public final class WorkerHealthResponses {
    private WorkerHealthResponses() {
    }

    public static void write(HttpExchange exchange, BooleanSupplier ready, UserMessageSource messages)
            throws IOException {
        var ok = ready.getAsBoolean();
        var status = ok ? 200 : 503;
        var body = ok ? WorkerHealthText.ok(messages) : WorkerHealthText.notReady(messages);
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
