package com.avandocmsg.messenger.desktop.sdk.queue;

import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OutgoingMessageQueue {

    public record PendingMessage(
        String id,
        String serverId,
        String chatId,
        String content,
        String threadId,
        int attempts
    ) {}

    private final Path file;

    public OutgoingMessageQueue(Path stateDir) {
        this.file = stateDir.resolve("outgoing-queue.json");
    }

    public synchronized List<PendingMessage> load() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        var type = JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, PendingMessage.class);
        List<PendingMessage> list = JsonSupport.mapper().readValue(Files.readString(file), type);
        return list == null ? List.of() : list;
    }

    public synchronized void enqueue(ChatRef chat, String content, String threadId) throws IOException {
        var list = new ArrayList<>(load());
        list.add(new PendingMessage(UUID.randomUUID().toString(), chat.serverId().value(), chat.chatId(), content, threadId, 0));
        flush(list);
    }

    public synchronized void remove(String id) throws IOException {
        var list = load().stream().filter(m -> !m.id().equals(id)).toList();
        flush(list);
    }

    public synchronized void bumpAttempts(String id) throws IOException {
        var list = new ArrayList<PendingMessage>();
        for (var m : load()) {
            if (m.id().equals(id)) {
                list.add(new PendingMessage(m.id(), m.serverId(), m.chatId(), m.content(), m.threadId(), m.attempts() + 1));
            } else {
                list.add(m);
            }
        }
        flush(list);
    }

    private void flush(List<PendingMessage> list) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, JsonSupport.mapper().writeValueAsString(list));
    }
}
