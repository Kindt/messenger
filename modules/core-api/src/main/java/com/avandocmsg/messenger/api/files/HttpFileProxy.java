package com.avandocmsg.messenger.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpFileProxy implements FileProxy {
    private static final String PROXY_FILES_RELATIVE = "v1/proxy/files/";
    private static final String HEADER_PROXY_AUTH = "X-Proxy-Auth";

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String authToken;

    public HttpFileProxy(String baseUrl, String authToken) {
        this.httpClient = HttpClient.newHttpClient();
        var normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.baseUri = URI.create(normalized);
        this.authToken = authToken;
    }

    private URI fileUri(String objectName) {
        return baseUri.resolve(PROXY_FILES_RELATIVE + objectName);
    }

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) throws IOException {
        try {
            var body = HttpRequest.BodyPublishers.ofInputStream(() -> data);
            var request = HttpRequest.newBuilder()
                .uri(fileUri(objectName))
                .header("Content-Type", contentType)
                .header(HEADER_PROXY_AUTH, authToken)
                .header("X-Object-Name", objectName)
                .PUT(body)
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("File proxy upload failed: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("File proxy upload interrupted", e);
        }
    }

    @Override
    public InputStream download(String objectName) throws IOException {
        try {
            var request = HttpRequest.newBuilder()
                .uri(fileUri(objectName))
                .header(HEADER_PROXY_AUTH, authToken)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                return null;
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("File proxy download interrupted", e);
        }
    }

    @Override
    public void delete(String objectName) throws IOException {
        try {
            var request = HttpRequest.newBuilder()
                .uri(fileUri(objectName))
                .header(HEADER_PROXY_AUTH, authToken)
                .DELETE()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("File proxy delete failed: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("File proxy delete interrupted", e);
        }
    }
}
