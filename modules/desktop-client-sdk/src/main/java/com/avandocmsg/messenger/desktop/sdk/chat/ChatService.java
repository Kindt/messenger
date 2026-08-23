package com.avandocmsg.messenger.desktop.sdk.chat;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SendMessageRequest;
import java.util.List;

public final class ChatService {

    private final KorusApiClient api;

    public ChatService(KorusApiClient api) {
        this.api = api;
    }

    public List<ChatDto> listChats(String token) {
        return api.listChats(token);
    }

    public List<MessageDto> listMessages(String token, ChatRef chat, String threadId) {
        return api.listMessages(token, chat.chatId(), threadId);
    }

    public MessageDto send(String token, ChatRef chat, String content, String threadId) {
        return api.sendMessage(token, chat.chatId(), new SendMessageRequest("text", content, null, threadId));
    }

    public void markRead(String token, ChatRef chat) {
        api.markRead(token, chat.chatId());
    }
}
