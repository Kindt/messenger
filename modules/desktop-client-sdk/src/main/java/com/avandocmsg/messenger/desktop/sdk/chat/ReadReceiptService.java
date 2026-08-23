package com.avandocmsg.messenger.desktop.sdk.chat;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;

/** Read receipts, typing indicator, unread counts. */
public final class ReadReceiptService {

    private final KorusApiClient api;

    public ReadReceiptService(KorusApiClient api) {
        this.api = api;
    }

    public void markChatRead(String token, ChatRef chat) {
        api.markRead(token, chat.chatId());
    }

    public void sendTyping(String token, ChatRef chat) {
        api.sendTyping(token, chat.chatId());
    }

    public int unreadCount(String token, ChatRef chat) {
        return api.unreadCount(token, chat.chatId()).unreadCount();
    }
}
