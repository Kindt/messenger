package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChatBanRepositoryH2Test {

    private HikariDataSource ds;
    private ChatBanRepository repo;
    private UUID chatId;
    private UUID moderatorId;
    private UUID bannedUser1;
    private UUID bannedUser2;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        moderatorId = UUID.randomUUID();
        bannedUser1 = UUID.randomUUID();
        bannedUser2 = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:chatban_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
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
                CREATE TABLE chat_bans (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  banned_by UUID NOT NULL REFERENCES users(id),
                  reason VARCHAR(512) NOT NULL DEFAULT '',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        insertUser(moderatorId);
        insertUser(bannedUser1);
        insertUser(bannedUser2);
        insertChat(chatId, moderatorId);
        var banId1 = UUID.randomUUID();
        var banId2 = UUID.randomUUID();
        AtomicInteger idx = new AtomicInteger();
        UUID[] banIds = {banId1, banId2};
        UuidGenerator gen = () -> banIds[idx.getAndIncrement()];
        var clock = Clock.fixed(Instant.parse("2024-07-01T12:00:00Z"), ZoneOffset.UTC);
        repo = new ChatBanRepository(ds, clock, gen);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void ban_findById_isBanned_unban() {
        var created = repo.ban(chatId, bannedUser1, moderatorId, "spam");
        assertNotNull(created);
        assertEquals("spam", created.reason());
        assertEquals(bannedUser1.toString(), created.userId());
        var fromDb = repo.findById(UUID.fromString(created.id())).orElseThrow();
        assertEquals(bannedUser1.toString(), fromDb.userId());
        assertTrue(repo.isBanned(chatId, bannedUser1));
        assertTrue(repo.unban(chatId, bannedUser1));
        assertFalse(repo.isBanned(chatId, bannedUser1));
        assertFalse(repo.unban(chatId, bannedUser1));
    }

    @Test
    void findByChatId_ordersNewestFirst_thenUnbanOne() throws Exception {
        var first = repo.ban(chatId, bannedUser1, moderatorId, "a");
        var second = repo.ban(chatId, bannedUser2, moderatorId, "b");
        assertNotNull(first);
        assertNotNull(second);
        setBanCreatedAt(UUID.fromString(first.id()), Instant.parse("2024-01-01T10:00:00Z"));
        setBanCreatedAt(UUID.fromString(second.id()), Instant.parse("2024-01-01T11:00:00Z"));
        var list = repo.findByChatId(chatId);
        assertEquals(2, list.size());
        assertEquals(bannedUser2.toString(), list.get(0).userId());
        assertEquals(bannedUser1.toString(), list.get(1).userId());
        assertTrue(repo.unban(chatId, bannedUser1));
        list = repo.findByChatId(chatId);
        assertEquals(1, list.size());
        assertEquals(bannedUser2.toString(), list.get(0).userId());
    }

    private void insertUser(UUID id) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private void insertChat(UUID id, UUID ownerId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.executeUpdate();
        }
    }

    private void setBanCreatedAt(UUID banId, Instant createdAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("UPDATE chat_bans SET created_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.from(createdAt));
            ps.setObject(2, banId);
            assertEquals(1, ps.executeUpdate());
        }
    }
}
