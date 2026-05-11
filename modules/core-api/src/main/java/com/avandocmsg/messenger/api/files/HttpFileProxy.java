package com.avandocmsg.messenger.api.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpFileProxy implements FileProxy {
    private static final Logger log = LoggerFactory.getLogger(HttpFileProxy.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authToken;

    public HttpFileProxy(String baseUrl, String authToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
    }

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) throws Exception {
        var body = HttpRequest.BodyPublishers.ofInputStream(() -> data);
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/proxy/files/" + objectName))
            .header("Content-Type", contentType)
            .header("X-Proxy-Auth", authToken)
            .header("X-Object-Name", objectName)
            .PUT(body)
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("File proxy upload failed: " + response.statusCode());
        }
    }

    @Override
    public InputStream download(String objectName) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/proxy/files/" + objectName))
            .header("X-Proxy-Auth", authToken)
            .GET()
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            return null;
        }
        return response.body();
    }

    @Override
    public void delete(String objectName) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/proxy/files/" + objectName))
            .header("X-Proxy-Auth", authToken)
            .DELETE()
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("File proxy delete failed: " + response.statusCode());
        }
    }
}
