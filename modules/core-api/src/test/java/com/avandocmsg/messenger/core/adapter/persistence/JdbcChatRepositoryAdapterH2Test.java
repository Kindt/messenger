package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcChatRepositoryAdapterH2Test {

    private HikariDataSource ds;
    private JdbcChatRepositoryAdapter adapter;
    private UUID chatId;
    private UUID memberId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:hex_chat_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256),
                  type VARCHAR(16) NOT NULL,
                  created_at TIMESTAMP NOT NULL
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
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, chatId);
            ps.setString(2, "Hex chat");
            ps.setString(3, "group");
            ps.setTimestamp(4, java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, memberId);
            ps.executeUpdate();
        }
        adapter = new JdbcChatRepositoryAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findById_mapsRow() {
        var chat = adapter.findById(ChatId.of(chatId)).orElseThrow();
        assertEquals("Hex chat", chat.title());
        assertEquals(ChatType.GROUP, chat.type());
    }

    @Test
    void isMember_reflectsChatMembers() {
        assertTrue(adapter.isMember(ChatId.of(chatId), UserId.of(memberId)));
        assertFalse(adapter.isMember(ChatId.of(chatId), UserId.of(UUID.randomUUID())));
    }

    @Test
    void memberRole_andBanned_reflectChatMembers() throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "UPDATE chat_members SET role = 'owner', banned = true WHERE chat_id = ? AND user_id = ?")) {
            ps.setObject(1, chatId);
            ps.setObject(2, memberId);
            ps.executeUpdate();
        }
        assertEquals("owner", adapter.memberRole(ChatId.of(chatId), UserId.of(memberId)).orElseThrow());
        assertTrue(adapter.isMemberBanned(ChatId.of(chatId), UserId.of(memberId)));
    }

    @Test
    void listMemberUserIds_returnsAllMembers() throws Exception {
        var otherId = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, otherId);
            ps.executeUpdate();
        }
        var ids = adapter.listMemberUserIds(ChatId.of(chatId));
        assertEquals(2, ids.size());
        assertTrue(ids.contains(UserId.of(memberId)));
        assertTrue(ids.contains(UserId.of(otherId)));
    }
}
