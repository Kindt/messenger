package com.avandocmsg.messenger.desktop.sdk.mesh;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.model.MeshCallSessionResponse;
import com.avandocmsg.messenger.desktop.sdk.model.StartMeshCallRequest;

/** Mesh WebRTC call sessions (same API path as web client). */
public final class MeshCallClient {

    private final KorusApiClient api;

    public MeshCallClient(KorusApiClient api) {
        this.api = api;
    }

    public MeshCallSessionResponse startSession(String token, String chatId, String mediaMode) {
        var mode = "video".equalsIgnoreCase(mediaMode) ? "video" : "audio";
        return api.startMeshCallSession(token, chatId, new StartMeshCallRequest(mode));
    }

    public MeshCallSessionResponse joinSession(String token, String chatId, String sessionId) {
        return api.joinMeshCallSession(token, chatId, sessionId);
    }
}
