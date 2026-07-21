package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageMentionRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.common.dto.MentionEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageMentionCoordinatorH2Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HikariDataSource ds;
    private UUID chatId;
    private UUID senderId;
    private UUID targetId;
    private RecordingMentionNats nats;
    private MessageMentionCoordinator coordinator;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:mention_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'group',
                  owner_id UUID REFERENCES users(id)
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
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE message_mentions (
                  message_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  mention_kind VARCHAR(8) NOT NULL DEFAULT 'user',
                  PRIMARY KEY (message_id, user_id)
                )
                """);
        }
        try (var c = ds.getConnection(); var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, senderId);
            ps.executeUpdate();
            ps.setObject(1, targetId);
            ps.executeUpdate();
        }
        var chatRepo = new JdbcChatRepositoryAdapter(ds);
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("INSERT INTO chats (id, title, type, owner_id) VALUES ('" + chatId + "', 'mention-test', 'group', '"
                + senderId + "')");
            st.execute("INSERT INTO chat_members (chat_id, user_id, role) VALUES ('" + chatId + "', '" + senderId
                + "', 'owner')");
            st.execute("INSERT INTO chat_members (chat_id, user_id, role) VALUES ('" + chatId + "', '" + targetId
                + "', 'member')");
        }
        nats = new RecordingMentionNats();
        coordinator = new MessageMentionCoordinator(chatRepo, new JdbcMessageMentionRepositoryAdapter(ds), nats);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void afterMessageSent_persistsMentionAndPublishesNats() throws Exception {
        var messageId = UUID.randomUUID();
        var content = "hello @" + targetId;
        coordinator.afterMessageSent(chatId, messageId, senderId, content, System.currentTimeMillis());

        assertTrue(mentionRepositoryCount(messageId, targetId) >= 1);
        assertEquals(1, nats.mentionSubjects.size());
        assertEquals(NatsSubjects.MSG_MENTION, nats.mentionSubjects.get(0));
        var event = MAPPER.readValue(nats.mentionPayloads.get(0), MentionEvent.class);
        assertEquals(messageId.toString(), event.messageId());
        assertEquals(targetId.toString(), event.mentionedUserId());
    }

    @Test
    void afterMessageSent_mentionAll_publishesSingleNatsEvent() throws Exception {
        var messageId = UUID.randomUUID();
        coordinator.afterMessageSent(chatId, messageId, senderId, "hello @all", System.currentTimeMillis());

        assertEquals(1, nats.mentionSubjects.size());
        var event = MAPPER.readValue(nats.mentionPayloads.get(0), MentionEvent.class);
        assertTrue(event.mentionAll());
        assertNull(event.mentionedUserId());
        assertTrue(mentionRepositoryCount(messageId, targetId) >= 1);
    }

    private int mentionRepositoryCount(UUID messageId, UUID userId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM message_mentions WHERE message_id = ? AND user_id = ?")) {
            ps.setObject(1, messageId);
            ps.setObject(2, userId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static final class RecordingMentionNats implements NatsOutboundPort {
        final List<String> mentionSubjects = new ArrayList<>();
        final List<byte[]> mentionPayloads = new ArrayList<>();

        @Override
        public void publish(String subject, byte[] payload) {
            if (NatsSubjects.MSG_MENTION.equals(subject)) {
                mentionSubjects.add(subject);
                mentionPayloads.add(payload != null ? payload.clone() : null);
            }
        }

        @Override
        public void flush(java.time.Duration timeout) {
            // no-op stub: flush not needed when capturing in-memory publish calls
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload, String userId) {
            // no-op stub: pipeline path not under test here
        }
    }
}
