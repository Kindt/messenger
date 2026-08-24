package com.avandocmsg.messenger.desktop.sdk.session;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.call.CallAudioMedia;
import com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient;
import com.avandocmsg.messenger.desktop.sdk.call.NativeCallAudioMedia;
import com.avandocmsg.messenger.desktop.sdk.chat.ChatService;
import com.avandocmsg.messenger.desktop.sdk.chat.ReadReceiptService;
import com.avandocmsg.messenger.desktop.sdk.web.WebUiUrlResolver;
import com.avandocmsg.messenger.desktop.sdk.files.FileTransferService;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SearchResponse;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsChatFacade;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public final class ApiDesktopSession implements DesktopSession {

    private final MultiServerSessionManager sessions;
    private final FileTransferService fileTransfer;
    private final Supplier<CallAudioMedia> mediaFactory;

    public ApiDesktopSession(MultiServerSessionManager sessions) {
        this(sessions, null);
    }

    public ApiDesktopSession(MultiServerSessionManager sessions, FileTransferService fileTransfer) {
        this(sessions, fileTransfer, NativeCallAudioMedia::new);
    }

    public ApiDesktopSession(
        MultiServerSessionManager sessions,
        FileTransferService fileTransfer,
        Supplier<CallAudioMedia> mediaFactory
    ) {
        this.sessions = sessions;
        this.fileTransfer = fileTransfer;
        this.mediaFactory = mediaFactory == null ? NativeCallAudioMedia::new : mediaFactory;
    }

    @Override
    public boolean isDemo() {
        return false;
    }

    @Override
    public List<ServerEntry> servers() {
        try {
            return sessions.activeServers();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CapabilitiesResponse capabilities(ServerId serverId, String username) {
        var token = requireToken(serverId, username);
        return client(serverId).capabilities(token);
    }

    @Override
    public List<ChatDto> listChats(ServerId serverId, String username) {
        var token = requireToken(serverId, username);
        return new ChatService(client(serverId)).listChats(token);
    }

    @Override
    public List<MessageDto> listMessages(ServerId serverId, String username, ChatRef chat, String threadId) {
        var token = requireToken(serverId, username);
        var api = client(serverId);
        var caps = api.capabilities(token);
        return mls(api, caps).listMessages(token, chat, threadId);
    }

    @Override
    public MessageDto send(ServerId serverId, String username, ChatRef chat, String content, String threadId) {
        var token = requireToken(serverId, username);
        var api = client(serverId);
        var caps = api.capabilities(token);
        var facade = mls(api, caps);
        facade.bootstrapIdentity(token);
        return facade.send(token, chat, content, threadId);
    }

    @Override
    public MessageDto sendFile(ServerId serverId, String username, ChatRef chat, Path file, String threadId)
        throws Exception {
        if (fileTransfer == null) {
            throw new IllegalStateException("file transfer not configured");
        }
        var token = requireToken(serverId, username);
        var entry = serverEntry(serverId);
        return fileTransfer.uploadAndSend(
            client(serverId),
            token,
            chat,
            file,
            threadId,
            entry.displayName()
        );
    }

    @Override
    public String startCall(ServerId serverId, String username, ChatRef chat, String mediaMode) throws Exception {
        var token = requireToken(serverId, username);
        var call = client(serverId).createCall(token, chat.chatId(), "group", mediaMode);
        var sessionId = call.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("call session id missing");
        }
        var webBase = WebUiUrlResolver.resolve(serverEntry(serverId));
        return WebUiUrlResolver.callJoinUrl(webBase, chat.chatId(), sessionId, mediaMode);
    }

    @Override
    public InProcessCallClient startLiveCall(
        ServerId serverId,
        String username,
        ChatRef chat,
        String mediaMode
    ) throws Exception {
        var token = requireToken(serverId, username);
        var client = new InProcessCallClient(client(serverId), mediaFactory);
        client.start(token, chat.chatId(), "group", mediaMode);
        return client;
    }

    @Override
    public InProcessCallClient joinLiveCall(
        ServerId serverId,
        String username,
        ChatRef chat,
        String sessionId
    ) throws Exception {
        var token = requireToken(serverId, username);
        var client = new InProcessCallClient(client(serverId), mediaFactory);
        client.join(token, chat.chatId(), sessionId);
        return client;
    }

    @Override
    public void sendTyping(ServerId serverId, String username, ChatRef chat) {
        var token = requireToken(serverId, username);
        new ReadReceiptService(client(serverId)).sendTyping(token, chat);
    }

    @Override
    public SearchResponse search(ServerId serverId, String username, String query) {
        var token = requireToken(serverId, username);
        return client(serverId).search(token, query);
    }

    @Override
    public void markRead(ServerId serverId, String username, ChatRef chat) {
        var token = requireToken(serverId, username);
        new ChatService(client(serverId)).markRead(token, chat);
    }

    private KorusApiClient client(ServerId serverId) {
        try {
            return sessions.clientFor(serverId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ServerEntry serverEntry(ServerId serverId) {
        try {
            return sessions.activeServers().stream()
                .filter(s -> s.serverId().equals(serverId.value()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("unknown server " + serverId.value()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MlsChatFacade mls(KorusApiClient api, com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse caps) {
        return new MlsChatFacade(api, caps, false);
    }

    private String requireToken(ServerId serverId, String username) {
        var token = sessions.token(serverId, username);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("no token for server " + serverId.value());
        }
        return token;
    }
}
