package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;

/** Unified inbox row: chat bound to a server. */
public record InboxRow(ServerEntry server, ChatDto chat) {
    public ChatRef chatRef() {
        return new ChatRef(new ServerId(server.serverId()), chat.resolvedId());
    }

    public String label() {
        var title = chat.title() == null || chat.title().isBlank() ? chat.resolvedId() : chat.title();
        return "[" + server.displayName() + "] " + title;
    }
}
