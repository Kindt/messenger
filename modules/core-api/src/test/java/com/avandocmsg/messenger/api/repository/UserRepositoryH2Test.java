package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryH2Test {

    private HikariDataSource ds;
    private UserRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:userrepo_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL UNIQUE,
                  display_name VARCHAR(128) NOT NULL,
                  phone VARCHAR(20),
                  hidden BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
                  last_seen_at TIMESTAMP,
                  org_id UUID,
                  privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false
                )
                """);
        }
        repo = new UserRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void create_then_findById_and_findByUsername() {
        var id = UUID.randomUUID();
        assertTrue(repo.create(id, "alice", "Alice D."));
        var p = repo.findById(id).orElseThrow();
        assertEquals("alice", p.username());
        assertEquals("Alice D.", p.displayName());
        assertEquals(id.toString(), p.id());
        assertNull(p.orgId());
        var byName = repo.findByUsername("alice").orElseThrow();
        assertEquals(id.toString(), byName.id());
    }

    @Test
    void updateProfile_updatesFields() {
        var id = UUID.randomUUID();
        assertTrue(repo.create(id, "bob", "Bob"));
        assertTrue(repo.updateProfile(id, "Robert", "+100"));
        var p = repo.findById(id).orElseThrow();
        assertEquals("Robert", p.displayName());
        assertEquals("+100", p.phone());
    }

    @Test
    void updatePresence_and_touchHeartbeat() {
        var id = UUID.randomUUID();
        assertTrue(repo.create(id, "carol", "Carol"));
        assertTrue(repo.updatePresence(id, "online"));
        var afterPresence = repo.findById(id).orElseThrow();
        assertEquals("online", afterPresence.presenceStatus());
        assertNotNull(afterPresence.lastSeenAt());
        var seenAfterPresence = afterPresence.lastSeenAt();
        assertTrue(repo.touchHeartbeat(id));
        var afterHb = repo.findById(id).orElseThrow();
        assertNotNull(afterHb.lastSeenAt());
        assertFalse(afterHb.lastSeenAt().isBefore(seenAfterPresence));
    }

}