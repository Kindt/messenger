package com.avandocmsg.messenger.desktop.sdk.session;

import com.avandocmsg.messenger.desktop.sdk.capabilities.CapabilityGate;
import com.avandocmsg.messenger.desktop.sdk.demo.DemoDataStore;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SearchResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.mls.DesktopMlsClient;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsWireCodec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DemoDesktopSession implements DesktopSession {

    private final DemoDataStore store;
    private final DesktopMlsClient mls = new DesktopMlsClient(null, true);

    public DemoDesktopSession(DemoDataStore store) {
        this.store = store;
    }

    @Override
    public boolean isDemo() {
        return true;
    }

    @Override
    public List<ServerEntry> servers() {
        return store.servers();
    }

    @Override
    public CapabilitiesResponse capabilities(ServerId serverId, String username) {
        return store.capabilities();
    }

    @Override
    public List<ChatDto> listChats(ServerId serverId, String username) {
        return store.chats();
    }

    @Override
    public List<MessageDto> listMessages(ServerId serverId, String username, ChatRef chat, String threadId) {
        var raw = store.messages(chat.chatId(), threadId);
        if (!e2ee()) {
            return raw;
        }
        var out = new ArrayList<MessageDto>(raw.size());
        for (var m : raw) {
            out.add(decrypt(m, chat.chatId()));
        }
        return out;
    }

    @Override
    public MessageDto send(ServerId serverId, String username, ChatRef chat, String content, String threadId) {
        var body = e2ee() ? mls.encrypt(content, chat.chatId(), null) : content;
        var stored = store.send(chat, body, threadId, username);
        return decrypt(stored, chat.chatId());
    }

    @Override
    public MessageDto sendFile(ServerId serverId, String username, ChatRef chat, Path file, String threadId) {
        return send(serverId, username, chat, "[file] " + file.getFileName(), threadId);
    }

    @Override
    public String startCall(ServerId serverId, String username, ChatRef chat, String title) {
        return startMeshCall(serverId, username, chat, "audio");
    }

    @Override
    public String startMeshCall(ServerId serverId, String username, ChatRef chat, String mediaMode) {
        return "demo://mesh-call?chat=" + chat.chatId() + "&mesh_mode=" + mediaMode;
    }

    @Override
    public void sendTyping(ServerId serverId, String username, ChatRef chat) {
        // demo no-op
    }

    @Override
    public SearchResponse search(ServerId serverId, String username, String query) {
        var q = query == null ? "" : query.toLowerCase();
        var hits = new java.util.ArrayList<com.avandocmsg.messenger.desktop.sdk.model.SearchHit>();
        for (var chat : store.chats()) {
            for (var m : store.messages(chat.resolvedId(), null)) {
                var row = decrypt(m, chat.resolvedId());
                if (row.content() != null && row.content().toLowerCase().contains(q)) {
                    hits.add(new com.avandocmsg.messenger.desktop.sdk.model.SearchHit(
                        "message", chat.resolvedId(), row.id(), row.content(), chat.title()
                    ));
                }
            }
        }
        return new com.avandocmsg.messenger.desktop.sdk.model.SearchResponse(hits, hits.size());
    }

    @Override
    public void markRead(ServerId serverId, String username, ChatRef chat) {
        // no-op demo
    }

    private boolean e2ee() {
        return new CapabilityGate(store.capabilities()).isEnabled(CapabilityGate.Feature.E2EE);
    }

    private MessageDto decrypt(MessageDto m, String chatId) {
        if (!e2ee() || m.content() == null || !MlsWireCodec.looksEncrypted(m.content())) {
            return m;
        }
        try {
            var plain = mls.decrypt(m.content(), chatId, null);
            return new MessageDto(m.id(), plain, m.threadId(), m.chatId(), m.senderId());
        } catch (Exception ex) {
            return new MessageDto(m.id(), "[E2EE] " + ex.getMessage(), m.threadId(), m.chatId(), m.senderId());
        }
    }
}
