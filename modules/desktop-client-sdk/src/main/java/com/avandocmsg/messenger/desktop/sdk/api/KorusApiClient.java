package com.avandocmsg.messenger.desktop.sdk.api;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.ConferenceResponse;
import com.avandocmsg.messenger.desktop.sdk.model.CreateChatRequest;
import com.avandocmsg.messenger.desktop.sdk.model.CreateConferenceRequest;
import com.avandocmsg.messenger.desktop.sdk.model.FileUploadResponse;
import com.avandocmsg.messenger.desktop.sdk.model.HealthResponse;
import com.avandocmsg.messenger.desktop.sdk.model.LoginRequest;
import com.avandocmsg.messenger.desktop.sdk.model.LoginResponse;
import com.avandocmsg.messenger.desktop.sdk.model.MeshCallSessionResponse;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SendMessageRequest;
import com.avandocmsg.messenger.desktop.sdk.model.StartMeshCallRequest;
import com.avandocmsg.messenger.desktop.sdk.model.SearchResponse;
import com.avandocmsg.messenger.desktop.sdk.model.UnreadCountResponse;
import com.avandocmsg.messenger.desktop.sdk.model.UserMeDto;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsSessionInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class KorusApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String HDR_AUTHORIZATION = "Authorization";
    private static final String AUTH_BEARER_PREFIX = "Bearer ";
    private static final String PATH_CHATS_PREFIX = "v1/chats/";

    private final OkHttpClient http;
    private final String apiBaseUrl;

    public KorusApiClient(OkHttpClient http, String apiBaseUrl) {
        this.http = Objects.requireNonNull(http, "http");
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl").trim();
    }

    public static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    public HealthResponse health() {
        return get("v1/health", null, HealthResponse.class);
    }

    public LoginResponse login(String username, String password) {
        var body = JsonSupport.mapper().valueToTree(new LoginRequest(username, password));
        return post("v1/auth/login", null, body.toString(), LoginResponse.class);
    }

    public UserMeDto me(String token) {
        return get("v1/users/me", token, UserMeDto.class);
    }

    public CapabilitiesResponse capabilities(String token) {
        return get("v1/platform/capabilities", token, CapabilitiesResponse.class);
    }

    public List<ChatDto> listChats(String token) {
        return get("v1/chats", token, new TypeReference<>() {});
    }

    public List<MessageDto> listMessages(String token, String chatId, String threadId) {
        var path = PATH_CHATS_PREFIX + chatId + "/messages";
        if (threadId != null && !threadId.isBlank()) {
            path += "?thread_id=" + threadId;
        }
        return get(path, token, new TypeReference<>() {});
    }

    public MessageDto sendMessage(String token, String chatId, SendMessageRequest request) {
        var json = JsonSupport.mapper().valueToTree(request).toString();
        return post(PATH_CHATS_PREFIX + chatId + "/messages", token, json, MessageDto.class);
    }

    public ChatDto createGroupChat(String token, String title, List<String> memberIds) {
        var json = JsonSupport.mapper().valueToTree(new CreateChatRequest("group", title, memberIds)).toString();
        return post("v1/chats", token, json, ChatDto.class);
    }

    public void markRead(String token, String chatId) {
        post(PATH_CHATS_PREFIX + chatId + "/read", token, "{}", Void.class);
    }

    public SearchResponse search(String token, String query) {
        var q = query == null ? "" : java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        var list = get("v1/search/messages?q=" + q + "&limit=20", token, new TypeReference<List<MessageDto>>() {});
        var hits = list.stream()
            .map(m -> new com.avandocmsg.messenger.desktop.sdk.model.SearchHit(
                "message",
                m.chatId(),
                m.id(),
                m.content(),
                null
            ))
            .toList();
        return new SearchResponse(hits, hits.size());
    }

    public com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot brandingPublic() {
        return get("v1/branding", null, com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot.class);
    }

    public com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot brandingMe(String token) {
        return get("v1/branding/me", token, com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot.class);
    }

    public FileUploadResponse uploadFile(String token, Path file, String filename) throws IOException {
        var name = filename == null || filename.isBlank()
            ? file.getFileName().toString()
            : filename;
        var mediaType = MediaType.parse("application/octet-stream");
        var body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name, RequestBody.create(Files.readAllBytes(file), mediaType))
            .build();
        var builder = new Request.Builder().url(api("v1/files/upload")).post(body);
        builder.header(HDR_AUTHORIZATION, AUTH_BEARER_PREFIX + token);
        return execute(builder.build(), FileUploadResponse.class);
    }

    public byte[] downloadFileContent(String token, String fileId) {
        var builder = new Request.Builder().url(api("v1/files/" + fileId + "/content")).get();
        builder.header(HDR_AUTHORIZATION, AUTH_BEARER_PREFIX + token);
        try (Response response = http.newCall(builder.build()).execute()) {
            var body = response.body() == null ? new byte[0] : response.body().bytes();
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code());
            }
            return body;
        } catch (IOException e) {
            throw new ApiException(-1, e.getMessage());
        }
    }

    public void sendTyping(String token, String chatId) {
        post(PATH_CHATS_PREFIX + chatId + "/typing", token, "{}", Void.class);
    }

    public UnreadCountResponse unreadCount(String token, String chatId) {
        return get(PATH_CHATS_PREFIX + chatId + "/unread-count", token, UnreadCountResponse.class);
    }

    public ConferenceResponse createConference(String token, String chatId, CreateConferenceRequest request) {
        var json = JsonSupport.mapper().valueToTree(request).toString();
        return post(PATH_CHATS_PREFIX + chatId + "/conferences", token, json, ConferenceResponse.class);
    }

    public MeshCallSessionResponse startMeshCallSession(String token, String chatId, StartMeshCallRequest request) {
        var json = JsonSupport.mapper().valueToTree(request).toString();
        return post(PATH_CHATS_PREFIX + chatId + "/mesh-calls/sessions", token, json, MeshCallSessionResponse.class);
    }

    public MeshCallSessionResponse joinMeshCallSession(String token, String chatId, String sessionId) {
        return post(
            PATH_CHATS_PREFIX + chatId + "/mesh-calls/sessions/" + sessionId + "/join",
            token,
            "{}",
            MeshCallSessionResponse.class
        );
    }

    public MlsSessionInfo mlsSession(String token, String chatId) {
        return get("v1/e2ee/mls/session/" + chatId, token, MlsSessionInfo.class);
    }

    public void uploadKeyPackage(
        String token,
        String publicKeyBase64,
        String signatureKeyBase64
    ) {
        var body = JsonSupport.mapper().createObjectNode();
        body.put("public_key_base64", publicKeyBase64);
        body.put("signature_key_base64", signatureKeyBase64);
        post("v1/e2ee/key-packages", token, body.toString(), Void.class);
    }

    private String api(String path) {
        var base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        var p = path.startsWith("/") ? path.substring(1) : path;
        if (base.endsWith("/api")) {
            return base + "/" + p;
        }
        return base + "/api/" + p;
    }

    private <T> T get(String path, String token, Class<T> type) {
        var builder = new Request.Builder().url(api(path)).get();
        if (token != null && !token.isBlank()) {
            builder.header(HDR_AUTHORIZATION, AUTH_BEARER_PREFIX + token);
        }
        return execute(builder.build(), type);
    }

    private <T> T get(String path, String token, TypeReference<T> type) {
        var builder = new Request.Builder().url(api(path)).get();
        if (token != null && !token.isBlank()) {
            builder.header(HDR_AUTHORIZATION, AUTH_BEARER_PREFIX + token);
        }
        return execute(builder.build(), type);
    }

    private <T> T post(String path, String token, String jsonBody, Class<T> type) {
        var builder = new Request.Builder()
            .url(api(path))
            .post(RequestBody.create(jsonBody, JSON));
        if (token != null && !token.isBlank()) {
            builder.header(HDR_AUTHORIZATION, AUTH_BEARER_PREFIX + token);
        }
        return execute(builder.build(), type);
    }

    private <T> T execute(Request request, Class<T> type) {
        try (Response response = http.newCall(request).execute()) {
            var body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code() + " " + body);
            }
            if (type == Void.class || body.isBlank()) {
                return null;
            }
            return JsonSupport.mapper().readValue(body, type);
        } catch (IOException e) {
            throw new ApiException(-1, e.getMessage());
        }
    }

    private <T> T execute(Request request, TypeReference<T> type) {
        try (Response response = http.newCall(request).execute()) {
            var body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code() + " " + body);
            }
            return JsonSupport.mapper().readValue(body, type);
        } catch (IOException e) {
            throw new ApiException(-1, e.getMessage());
        }
    }
}
