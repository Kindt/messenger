package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.UserId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUserRepositoryAdapterH2Test {

    private HikariDataSource ds;
    private JdbcUserRepositoryAdapter adapter;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:hex_user_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL,
                  display_name VARCHAR(128) NOT NULL,
                  phone VARCHAR(20),
                  hidden BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP,
                  presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
                  last_seen_at TIMESTAMP,
                  org_id UUID,
                  privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false,
                  ui_locale VARCHAR(8),
                  custom_status_text VARCHAR(128) NOT NULL DEFAULT '',
                  dnd_until TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO users (id, username, display_name, phone, hidden, created_at, presence_status,
                     last_seen_at, org_id, privacy_disable_read_receipts)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            ps.setObject(1, userId);
            ps.setString(2, "hex-user");
            ps.setString(3, "Hex User");
            ps.setString(4, "+42");
            ps.setBoolean(5, false);
            ps.setTimestamp(6, java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            ps.setString(7, "online");
            ps.setTimestamp(8, java.sql.Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")));
            ps.setObject(9, null);
            ps.setBoolean(10, true);
            ps.executeUpdate();
        }
        adapter = new JdbcUserRepositoryAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findById_mapsRow() {
        var profile = adapter.findById(UserId.of(userId)).orElseThrow();
        assertEquals("hex-user", profile.username());
        assertEquals("Hex User", profile.displayName());
        assertEquals("+42", profile.phone());
        assertTrue(profile.privacyDisableReadReceipts());
    }

    @Test
    void updateProfile_changesDisplayNameAndPhone() {
        assertTrue(adapter.updateProfile(UserId.of(userId), "Renamed", "+99"));
        var profile = adapter.findById(UserId.of(userId)).orElseThrow();
        assertEquals("Renamed", profile.displayName());
        assertEquals("+99", profile.phone());
    }

    @Test
    void updatePresence_changesStatus() {
        assertTrue(adapter.updatePresence(UserId.of(userId), "away"));
        assertEquals("away", adapter.findById(UserId.of(userId)).orElseThrow().presenceStatus());
    }

    @Test
    void updatePrivacy_togglesReadReceipts() {
        assertTrue(adapter.updatePrivacy(UserId.of(userId), false));
        assertFalse(adapter.findById(UserId.of(userId)).orElseThrow().privacyDisableReadReceipts());
    }

    @Test
    void updateUiLocale_persistsCode() {
        assertTrue(adapter.updateUiLocale(UserId.of(userId), "en"));
        assertEquals("en", adapter.findById(UserId.of(userId)).orElseThrow().uiLocale());
    }

    @Test
    void touchHeartbeat_updatesLastSeen() {
        assertTrue(adapter.touchHeartbeat(UserId.of(userId)));
        assertNotNull(adapter.findById(UserId.of(userId)).orElseThrow().lastSeenAt());
    }

    @Test
    void createLocalUser_insertsRow() {
        var newId = UUID.randomUUID();
        assertTrue(adapter.createLocalUser(UserId.of(newId), "reg-user", "Reg User"));
        var profile = adapter.findById(UserId.of(newId)).orElseThrow();
        assertEquals("reg-user", profile.username());
        assertEquals("Reg User", profile.displayName());
    }
}
