package com.avandocmsg.messenger.desktop.sdk.session;

import com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SearchResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.nio.file.Path;
import java.util.List;

/** Unified session API for live API or offline demo. */
public interface DesktopSession {

    boolean isDemo();

    List<ServerEntry> servers();

    CapabilitiesResponse capabilities(ServerId serverId, String username);

    List<ChatDto> listChats(ServerId serverId, String username);

    List<MessageDto> listMessages(ServerId serverId, String username, ChatRef chat, String threadId);

    MessageDto send(ServerId serverId, String username, ChatRef chat, String content, String threadId);

    MessageDto sendFile(ServerId serverId, String username, ChatRef chat, Path file, String threadId) throws Exception;

    /** Starts a provider-neutral call and returns its temporary web handoff URL. */
    String startCall(ServerId serverId, String username, ChatRef chat, String mediaMode) throws Exception;

    /** Starts an in-process native media call (no browser). */
    InProcessCallClient startLiveCall(ServerId serverId, String username, ChatRef chat, String mediaMode)
        throws Exception;

    /** Joins an existing in-process native media call (no browser). */
    InProcessCallClient joinLiveCall(
        ServerId serverId,
        String username,
        ChatRef chat,
        String sessionId
    ) throws Exception;

    void sendTyping(ServerId serverId, String username, ChatRef chat);

    SearchResponse search(ServerId serverId, String username, String query);

    void markRead(ServerId serverId, String username, ChatRef chat);
}
