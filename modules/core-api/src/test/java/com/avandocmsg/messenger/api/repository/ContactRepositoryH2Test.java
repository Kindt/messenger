package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ContactRepository#list} / {@link ContactRepository#remove}; строки в {@code contacts} — JDBC INSERT
 * (аналогично {@link BlockRepositoryH2Test}: H2 и {@code ON CONFLICT DO NOTHING} в {@link ContactRepository#add}).
 */
class ContactRepositoryH2Test {

    private HikariDataSource ds;
    private ContactRepository repo;
    private UUID userId;
    private UUID contact1Id;
    private UUID contact2Id;

    @BeforeEach
    void init() throws Exception {
        userId = UUID.randomUUID();
        contact1Id = UUID.randomUUID();
        contact2Id = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:ctrepo_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL,
                  display_name VARCHAR(128) NOT NULL,
                  phone VARCHAR(20)
                )
                """);
            st.execute("""
                CREATE TABLE contacts (
                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  contact_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  added_at TIMESTAMP NOT NULL,
                  PRIMARY KEY (user_id, contact_user_id)
                )
                """);
        }
        insertUser(userId, "me", "Me", null);
        insertUser(contact1Id, "c1", "Contact One", "+1");
        insertUser(contact2Id, "c2", "Contact Two", "+2");
        repo = new ContactRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void list_ordersByAddedAtDesc_remove() throws Exception {
        insertContact(userId, contact1Id, Instant.parse("2023-01-01T00:00:00Z"));
        insertContact(userId, contact2Id, Instant.parse("2024-01-01T00:00:00Z"));
        var list = repo.list(userId);
        assertEquals(2, list.size());
        assertEquals(contact2Id.toString(), list.get(0).id());
        assertEquals("Contact Two", list.get(0).displayName());
        assertEquals("+2", list.get(0).phone());
        assertEquals(contact1Id.toString(), list.get(1).id());
        assertTrue(repo.remove(userId, contact2Id));
        list = repo.list(userId);
        assertEquals(1, list.size());
        assertEquals(contact1Id.toString(), list.get(0).id());
        assertFalse(repo.remove(userId, contact2Id));
        assertTrue(repo.remove(userId, contact1Id));
        assertTrue(repo.list(userId).isEmpty());
    }

    private void insertUser(UUID id, String username, String display, String phone) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id, username, display_name, phone) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, username);
            ps.setString(3, display);
            ps.setString(4, phone);
            ps.executeUpdate();
        }
    }

    private void insertContact(UUID owner, UUID contactUser, Instant addedAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO contacts (user_id, contact_user_id, added_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, owner);
            ps.setObject(2, contactUser);
            ps.setTimestamp(3, java.sql.Timestamp.from(addedAt));
            ps.executeUpdate();
        }
    }
}
