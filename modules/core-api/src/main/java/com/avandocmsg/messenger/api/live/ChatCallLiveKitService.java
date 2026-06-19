package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.live.dto.JoinLiveKitCallResponse;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import java.util.Optional;
import java.util.UUID;

/** Group call SFU via LiveKit — room per chat, reuse token service (spec 019 US5). */
public class ChatCallLiveKitService {
    private static final int TOKEN_TTL_SEC = 3600;

    private final ChatPersistencePort chatPersistencePort;
    private final LiveKitTokenService liveKitTokenService;

    public ChatCallLiveKitService(ChatPersistencePort chatPersistencePort, LiveKitTokenService liveKitTokenService) {
        this.chatPersistencePort = chatPersistencePort;
        this.liveKitTokenService = liveKitTokenService;
    }

    public boolean groupCallSfuEnabled() {
        return liveKitTokenService.enabled();
    }

    public Optional<JoinLiveKitCallResponse> join(UUID chatId, UUID userId) {
        if (!groupCallSfuEnabled()) {
            return Optional.empty();
        }
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        var room = "call-" + chatId.toString().replace("-", "");
        var identity = userId.toString();
        var token = liveKitTokenService.createAccessToken(room, identity, true, TOKEN_TTL_SEC);
        return Optional.of(new JoinLiveKitCallResponse(room, liveKitTokenService.livekitUrl(), token));
    }
}
