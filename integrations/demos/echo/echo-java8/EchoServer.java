import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Spec 014 legacy echo demo — Java 8 compatible sidecar. */
public final class EchoServer {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 8);
        server.createContext("/health", new TextHandler("ok"));
        server.createContext("/v1/plugin/handle", new PluginHandler());
        server.start();
        System.out.println("echo-java8 on :" + port);
        Thread.currentThread().join();
    }

    static final class TextHandler implements HttpHandler {
        private final String body;

        TextHandler(String body) {
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static final class PluginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String raw = new String(readAll(exchange), StandardCharsets.UTF_8);
            String text = extractText(raw).trim().toLowerCase();
            String msg = "Echo Java 8 sidecar. Try: ping, /echo text";
            if ("ping".equals(text)) {
                msg = "pong (echo-java8)";
            } else if (raw.contains("/echo ")) {
                int idx = raw.indexOf("/echo ");
                if (idx >= 0) {
                    msg = raw.substring(idx + 6).replaceAll("[\"}\\]]+$", "").trim();
                }
            }
            String json = "{\"messages\":[{\"text\":\"" + escape(msg) + "\",\"format\":\"markdown\"}]}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private static byte[] readAll(HttpExchange exchange) throws IOException {
            InputStream in = exchange.getRequestBody();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }

        private static String extractText(String raw) {
            int key = raw.indexOf("\"text\"");
            if (key < 0) {
                return "";
            }
            int colon = raw.indexOf(':', key);
            int q1 = raw.indexOf('"', colon + 1);
            int q2 = raw.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) {
                return "";
            }
            return raw.substring(q1 + 1, q2);
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
