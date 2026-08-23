package com.avandocmsg.messenger.desktop.sdk.demo;

import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ChatDto;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SearchHit;
import com.avandocmsg.messenger.desktop.sdk.model.SearchResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory demo backend — no HTTP. Mirrors web-client flows for offline QA.
 */
public final class DemoDataStore {

    public static final String SERVER_A = "demo-server-a";
    public static final String SERVER_B = "demo-server-b";
    public static final String DEMO_USER = "demo-user";
    public static final String PEER_ALICE = "user-alice";

    private final Map<String, List<MessageDto>> messagesByChat = new ConcurrentHashMap<>();
    private final List<ServerEntry> servers = new ArrayList<>();
    private final List<ChatDto> chats = new ArrayList<>();

    public DemoDataStore() {
        servers.add(new ServerEntry(SERVER_A, "Demo Corp", "demo://corp"));
        servers.add(new ServerEntry(SERVER_B, "Demo Partner", "demo://partner"));

        var chat1 = new ChatDto("chat-general", null, "General");
        var chat2 = new ChatDto("chat-dev", null, "Dev @corp");
        var chat3 = new ChatDto("chat-ext", null, "Partner sync");
        chats.add(chat1);
        chats.add(chat2);
        chats.add(chat3);

        putMessages("chat-general", List.of(
            new MessageDto("m1", "Welcome @alice — demo mode", null, "chat-general", PEER_ALICE),
            new MessageDto("m2", "Thread root", null, "chat-general", PEER_ALICE),
            new MessageDto("m3", "Reply in thread", "m2", "chat-general", PEER_ALICE)
        ));
        putMessages("chat-dev", List.of(
            new MessageDto("m4", "Deploy @all tonight", null, "chat-dev", PEER_ALICE)
        ));
        putMessages("chat-ext", List.of(
            new MessageDto("m5", "Contact user-42 on server B only", null, "chat-ext", PEER_ALICE)
        ));
    }

    public List<ServerEntry> servers() {
        return List.copyOf(servers);
    }

    public List<ChatDto> chats() {
        return List.copyOf(chats);
    }

    public CapabilitiesResponse capabilities() {
        var addons = new LinkedHashMap<String, com.avandocmsg.messenger.desktop.sdk.model.AddonCapability>();
        addons.put("addon-search", new com.avandocmsg.messenger.desktop.sdk.model.AddonCapability(true));
        addons.put("addon-engage", new com.avandocmsg.messenger.desktop.sdk.model.AddonCapability(true));
        addons.put("addon-productivity", new com.avandocmsg.messenger.desktop.sdk.model.AddonCapability(false));
        addons.put("addon-integrations", new com.avandocmsg.messenger.desktop.sdk.model.AddonCapability(false));
        addons.put("addon-e2ee", new com.avandocmsg.messenger.desktop.sdk.model.AddonCapability(true));
        return new CapabilitiesResponse(addons, List.of("chat", "files", "websocket", "sql_search", "e2ee"));
    }

    public List<MessageDto> messages(String chatId, String threadId) {
        var all = messagesByChat.getOrDefault(chatId, List.of());
        if (threadId == null || threadId.isBlank()) {
            return all.stream().filter(m -> m.threadId() == null || m.threadId().isBlank()).toList();
        }
        return all.stream().filter(m -> threadId.equals(m.threadId())).toList();
    }

    public MessageDto send(ChatRef ref, String content, String threadId, String senderId) {
        var id = UUID.randomUUID().toString();
        var msg = new MessageDto(id, content, threadId, ref.chatId(), senderId);
        var list = new ArrayList<>(messagesByChat.getOrDefault(ref.chatId(), List.of()));
        list.add(msg);
        messagesByChat.put(ref.chatId(), List.copyOf(list));
        return msg;
    }

    public SearchResponse search(String query) {
        var q = query == null ? "" : query.toLowerCase();
        var hits = new ArrayList<SearchHit>();
        for (var chat : chats) {
            for (var m : messagesByChat.getOrDefault(chat.resolvedId(), List.of())) {
                if (m.content() != null && m.content().toLowerCase().contains(q)) {
                    hits.add(new SearchHit("message", chat.resolvedId(), m.id(), m.content(), chat.title()));
                }
            }
        }
        return new SearchResponse(hits, hits.size());
    }

    private void putMessages(String chatId, List<MessageDto> msgs) {
        messagesByChat.put(chatId, List.copyOf(msgs));
    }
}
