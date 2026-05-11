package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ChatRepository#chatExists} и {@link ChatRepository#findOrgIdForRetentionOverlay} (SQL без полного чат-API).
 */
class ChatRepositoryRetentionOverlayH2Test {

    private HikariDataSource ds;
    private ChatRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:chatov_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  org_id UUID
                )
                """);
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
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  user_id UUID NOT NULL REFERENCES users(id),
                  role VARCHAR(16) NOT NULL
                )
                """);
        }
        repo = new ChatRepository(ds, Clock.systemUTC(), UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void chatExists_falseWhenMissing() {
        assertFalse(repo.chatExists(UUID.randomUUID()));
    }

    @Test
    void chatExists_trueWhenRowPresent() throws Exception {
        var ownerId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        insertUser(ownerId, null);
        insertChat(chatId, ownerId);
        assertTrue(repo.chatExists(chatId));
    }

    @Test
    void findOrgId_prefersOwnerOrg() throws Exception {
        var orgOwner = UUID.randomUUID();
        var orgMember = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        insertUser(ownerId, orgOwner);
        insertUser(memberId, orgMember);
        insertChat(chatId, ownerId);
        insertMember(chatId, memberId, "member");
        assertEquals(Optional.of(orgOwner), repo.findOrgIdForRetentionOverlay(chatId));
    }

    @Test
    void findOrgId_whenOwnerOrgNull_usesMemberRoleOrder() throws Exception {
        var orgAdmin = UUID.randomUUID();
        var orgMember = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var adminId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        insertUser(ownerId, null);
        insertUser(adminId, orgAdmin);
        insertUser(memberId, orgMember);
        insertChat(chatId, ownerId);
        insertMember(chatId, memberId, "member");
        insertMember(chatId, adminId, "admin");
        assertEquals(Optional.of(orgAdmin), repo.findOrgIdForRetentionOverlay(chatId));
    }

    @Test
    void findOrgId_whenOwnerOrgNull_prefersOwnerRoleAmongMembers() throws Exception {
        var orgA = UUID.randomUUID();
        var orgB = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var u1 = UUID.randomUUID();
        var u2 = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        insertUser(ownerId, null);
        insertUser(u1, orgA);
        insertUser(u2, orgB);
        insertChat(chatId, ownerId);
        insertMember(chatId, u2, "member");
        insertMember(chatId, u1, "owner");
        assertEquals(Optional.of(orgA), repo.findOrgIdForRetentionOverlay(chatId));
    }

    @Test
    void findOrgId_emptyWhenNoOrgOnOwnerOrMembers() throws Exception {
        var ownerId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        insertUser(ownerId, null);
        insertUser(memberId, null);
        insertChat(chatId, ownerId);
        insertMember(chatId, memberId, "admin");
        assertTrue(repo.findOrgIdForRetentionOverlay(chatId).isEmpty());
    }

    private void insertUser(UUID id, UUID orgId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id, org_id) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, orgId);
            ps.executeUpdate();
        }
    }

    private void insertChat(UUID chatId, UUID ownerId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, ownerId);
            ps.executeUpdate();
        }
    }

    private void insertMember(UUID chatId, UUID userId, String role) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chat_members (chat_id, user_id, role) VALUES (?, ?, ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }
}
