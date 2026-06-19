package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.live.dto.LiveSessionResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Live streaming sessions. */
public interface LiveSessionPort {
    String newRoomName();

    Optional<LiveSessionResponse> insert(UUID chatId, UUID createdBy, String title, String roomName);

    Optional<LiveSessionResponse> findById(UUID sessionId);

    List<LiveSessionResponse> listForChat(UUID chatId, boolean activeOnly);

    int countActiveViewers(UUID sessionId);

    Optional<String> viewerRole(UUID sessionId, UUID userId);

    boolean join(UUID sessionId, UUID userId, String role);

    boolean leave(UUID sessionId, UUID userId);

    Optional<UUID> findCreatorId(UUID sessionId);

    boolean endSession(UUID sessionId);

    boolean updateDvrPlaylist(UUID sessionId, String url);

    boolean recordModerationEvent(UUID sessionId, UUID actorUserId, String action, String reason);

    boolean setModerationState(UUID sessionId, String state);
}
