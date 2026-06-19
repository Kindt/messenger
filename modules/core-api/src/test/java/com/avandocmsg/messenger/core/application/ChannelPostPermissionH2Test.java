package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPostPermissionH2Test {

    private HikariDataSource ds;
    private ChatRepository chatRepository;
    private MessageApplicationService messageService;
    private UUID channelId;
    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void init() throws Exception {
        channelId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:channel_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'group',
                  owner_id UUID REFERENCES users(id),
                  channel_post_policy VARCHAR(16) NOT NULL DEFAULT 'admins_only',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE chat_members (
                  chat_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'member',
                  banned BOOLEAN NOT NULL DEFAULT false,
                  PRIMARY KEY (chat_id, user_id)
                )
                """);
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id),
                  sender_id UUID NOT NULL REFERENCES users(id),
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  reply_to_msg_id UUID,
                  thread_id UUID,
                  client_msg_id VARCHAR(64),
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  visibility_ttl_seconds INT,
                  attachment_file_id UUID,
                  voice_duration_ms INT,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  edited_at TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection(); var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, ownerId);
            ps.executeUpdate();
            ps.setObject(1, memberId);
            ps.executeUpdate();
        }
        chatRepository = new ChatRepository(ds, Clock.systemUTC(), UuidGenerator.standard());
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("INSERT INTO chats (id, title, type, owner_id, channel_post_policy, created_at) VALUES ('"
                + channelId + "', 'news', 'channel', '" + ownerId + "', 'admins_only', CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO chat_members (chat_id, user_id, role) VALUES ('" + channelId + "', '" + ownerId
                + "', 'owner')");
            st.execute("INSERT INTO chat_members (chat_id, user_id, role) VALUES ('" + channelId + "', '" + memberId
                + "', 'member')");
        }
        var messageRepo = new MessageRepository(ds, Clock.systemUTC());
        var adapter = new JdbcMessageRepositoryAdapter(messageRepo);
        var sendCoordinator = new MessageSendCoordinator(
            adapter,
            new JdbcChatRepositoryAdapter(ds),
            null,
            null,
            NatsOutboundPort.noop(),
            UuidGenerator.standard(),
            null,
            null);
        messageService = new MessageApplicationService(adapter, new JdbcChatRepositoryAdapter(ds), null, sendCoordinator);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void memberCannotPost_inChannel() {
        var denied = messageService.sendBlockedReason(channelId, memberId);
        assertTrue(denied.isPresent());
        assertEquals("error.message.send_denied.channel_readonly", denied.get());
        var sent = messageService.sendMessage(
            channelId,
            memberId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, "legacy", null),
            null);
        assertNull(sent);
    }

    @Test
    void ownerCanPost_inChannel() {
        assertTrue(messageService.sendBlockedReason(channelId, ownerId).isEmpty());
        var sent = messageService.sendMessage(
            channelId,
            ownerId,
            new SendMessageRequest("text", "announcement", null, null, null, null, null, "legacy", null),
            null);
        assertNotNull(sent);
        assertEquals("announcement", sent.content());
    }
}
