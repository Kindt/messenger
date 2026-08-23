package com.avandocmsg.messenger.desktop.sdk.ws;

import java.io.Closeable;
import java.util.Objects;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WsSession implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(WsSession.class);

    private volatile WebSocket socket;

    public void connect(OkHttpClient http, String urlWithToken, WsEventHandler handler) {
        close();
        var request = new Request.Builder().url(Objects.requireNonNull(urlWithToken)).build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handler.onRawMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                LOG.debug("ws failure: {}", t.getMessage());
            }
        });
    }

    @Override
    public void close() {
        var ws = socket;
        socket = null;
        if (ws != null) {
            ws.close(1000, "desktop-close");
        }
    }
}
