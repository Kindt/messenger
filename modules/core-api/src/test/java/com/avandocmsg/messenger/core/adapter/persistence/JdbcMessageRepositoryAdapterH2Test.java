package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcMessageRepositoryAdapterH2Test {

    private HikariDataSource ds;
    private JdbcMessageRepositoryAdapter adapter;
    private UUID chatId;
    private UUID senderId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:hexmsg_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
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
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  sender_id UUID NOT NULL REFERENCES users(id),
                  client_msg_id VARCHAR(64),
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  reply_to_msg_id UUID,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  visibility_ttl_seconds INT,
                  attachment_file_id UUID,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  edited_at TIMESTAMP
                )
                """);
            st.execute("INSERT INTO users (id) VALUES ('" + senderId + "')");
            st.execute("INSERT INTO chats (id, owner_id) VALUES ('" + chatId + "', '" + senderId + "')");
        }
        adapter = new JdbcMessageRepositoryAdapter(new MessageRepository(ds, Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insert_persistsAndFindById_returnsDomainMessage() {
        var id = UUID.randomUUID();
        var inserted = adapter.insert(new MessageInsert(
            MessageId.of(id),
            ChatId.of(chatId),
            UserId.of(senderId),
            "text",
            "hello hex",
            null,
            "client-1",
            3600,
            null));
        assertTrue(inserted.isPresent());
        assertEquals("hello hex", inserted.get().content());
        assertEquals(3600, inserted.get().visibilityTtlSeconds());

        var found = adapter.findById(MessageId.of(id));
        assertTrue(found.isPresent());
        assertEquals(id, found.get().id().value());
        assertEquals(chatId, found.get().chatId().value());
        assertTrue(found.get().createdAt().isBefore(Instant.now().plusSeconds(5)));
    }
}
