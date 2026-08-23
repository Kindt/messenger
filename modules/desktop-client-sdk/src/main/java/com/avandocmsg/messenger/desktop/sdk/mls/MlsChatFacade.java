package com.avandocmsg.messenger.desktop.sdk.mls;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.capabilities.CapabilityGate;
import com.avandocmsg.messenger.desktop.sdk.chat.ChatService;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import java.util.ArrayList;
import java.util.List;

/** Encrypt outbound / decrypt inbound when addon-e2ee is on. */
public final class MlsChatFacade {

    private final KorusApiClient api;
    private final ChatService chat;
    private final DesktopMlsClient mls;
    private final CapabilitiesResponse caps;

    public MlsChatFacade(KorusApiClient api, CapabilitiesResponse caps, boolean demo) {
        this.api = api;
        this.chat = new ChatService(api);
        this.mls = new DesktopMlsClient(api, demo);
        this.caps = caps;
    }

    public List<MessageDto> listMessages(String token, ChatRef chat, String threadId) {
        var raw = this.chat.listMessages(token, chat, threadId);
        if (!e2ee()) {
            return raw;
        }
        var out = new ArrayList<MessageDto>(raw.size());
        for (var m : raw) {
            out.add(decryptRow(m, chat.chatId(), token));
        }
        return out;
    }

    public MessageDto send(String token, ChatRef chat, String content, String threadId) {
        if (!e2ee()) {
            return this.chat.send(token, chat, content, threadId);
        }
        var enc = mls.encrypt(content, chat.chatId(), token);
        return decryptRow(this.chat.send(token, chat, enc, threadId), chat.chatId(), token);
    }

    public void bootstrapIdentity(String token) {
        if (!e2ee() || token == null) {
            return;
        }
        try {
            MlsIdentityBootstrap.ensureKeyPackageUploaded(api, token);
        } catch (Exception ignored) {
            // best-effort before first encrypted send
        }
    }

    private MessageDto decryptRow(MessageDto m, String chatId, String token) {
        if (m.content() == null || !MlsWireCodec.looksEncrypted(m.content())) {
            return m;
        }
        try {
            var plain = mls.decrypt(m.content(), chatId, token);
            return new MessageDto(m.id(), plain, m.threadId(), m.chatId(), m.senderId());
        } catch (Exception ex) {
            return new MessageDto(m.id(), "[E2EE] " + ex.getMessage(), m.threadId(), m.chatId(), m.senderId());
        }
    }

    private boolean e2ee() {
        return new CapabilityGate(caps).isEnabled(CapabilityGate.Feature.E2EE);
    }
}
