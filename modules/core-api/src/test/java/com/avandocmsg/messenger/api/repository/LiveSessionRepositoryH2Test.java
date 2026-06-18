package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LiveSessionRepositoryH2Test {

    private HikariDataSource ds;
    private LiveSessionRepository repo;
    private UUID chatId;
    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:live_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(64),
                  display_name VARCHAR(128)
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
                CREATE TABLE live_sessions (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  created_by UUID NOT NULL REFERENCES users(id),
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  status VARCHAR(16) NOT NULL DEFAULT 'active',
                  mode VARCHAR(16) NOT NULL DEFAULT 'webrtc',
                  room_name VARCHAR(160) NOT NULL UNIQUE,
                  max_viewers INT NOT NULL DEFAULT 200,
                  viewer_count INT NOT NULL DEFAULT 0,
                  dvr_playlist_url VARCHAR(2048),
                  dvr_started_at TIMESTAMP,
                  moderation_state VARCHAR(32) NOT NULL DEFAULT 'open',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  ended_at TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE live_session_moderation_events (
                  id UUID PRIMARY KEY,
                  session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
                  actor_user_id UUID NOT NULL REFERENCES users(id),
                  action VARCHAR(64) NOT NULL,
                  reason VARCHAR(512),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE live_session_viewers (
                  session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  role VARCHAR(16) NOT NULL DEFAULT 'viewer',
                  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  left_at TIMESTAMP,
                  PRIMARY KEY (session_id, user_id)
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, userId);
            ps.setString(2, "host");
            ps.setString(3, "Host");
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO live_sessions (id, chat_id, created_by, title, room_name, max_viewers)
                 VALUES (?, ?, ?, 'Demo', 'korus-live-test', 200)
                 """)) {
            ps.setObject(1, sessionId);
            ps.setObject(2, chatId);
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
        var appConfig = new AppConfig() {
            @Override
            public String livekitUrl() {
                return "wss://lk.test";
            }

            @Override
            public String livekitApiKey() {
                return "k";
            }

            @Override
            public String livekitApiSecret() {
                return "s";
            }
        };
        repo = new LiveSessionRepository(ds, appConfig, UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void joinLeave_updatesViewerCount() throws Exception {
        var viewer = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, viewer);
            ps.setString(2, "viewer");
            ps.setString(3, "Viewer");
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO live_session_viewers (session_id, user_id, role) VALUES (?, ?, ?)")) {
            ps.setObject(1, sessionId);
            ps.setObject(2, userId);
            ps.setString(3, "host");
            ps.executeUpdate();
            ps.setObject(2, viewer);
            ps.setString(3, "viewer");
            ps.executeUpdate();
        }
        assertEquals(2, repo.countActiveViewers(sessionId));
        assertTrue(repo.leave(sessionId, viewer));
        assertEquals(1, repo.countActiveViewers(sessionId));
    }

    @Test
    void endSession_marksEnded() {
        assertTrue(repo.endSession(sessionId));
        var found = repo.findById(sessionId);
        assertTrue(found.isPresent());
        assertEquals("ended", found.get().status());
    }

    @Test
    void listForChat_activeOnly() {
        assertEquals(1, repo.listForChat(chatId, true).size());
        assertTrue(repo.endSession(sessionId));
        assertTrue(repo.listForChat(chatId, true).isEmpty());
        assertEquals(1, repo.listForChat(chatId, false).size());
    }

    @Test
    void listForChat_returnsLiveViewerCountWhenColumnStale() throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("UPDATE live_sessions SET viewer_count = 0 WHERE id = ?")) {
            ps.setObject(1, sessionId);
            ps.executeUpdate();
        }
        var viewer = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, viewer);
            ps.setString(2, "viewer");
            ps.setString(3, "Viewer");
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO live_session_viewers (session_id, user_id, role) VALUES (?, ?, ?)")) {
            ps.setObject(1, sessionId);
            ps.setObject(2, userId);
            ps.setString(3, "host");
            ps.executeUpdate();
            ps.setObject(2, viewer);
            ps.setString(3, "viewer");
            ps.executeUpdate();
        }
        var listed = repo.listForChat(chatId, true);
        assertEquals(1, listed.size());
        assertEquals(2, listed.getFirst().viewerCount());
    }

    @Test
    void recordModerationEvent_insertsRowAndUpdatesState() throws Exception {
        assertTrue(repo.recordModerationEvent(sessionId, userId, "slow_mode", "test reason"));
        assertTrue(repo.setModerationState(sessionId, "slow_mode"));
        var found = repo.findById(sessionId);
        assertTrue(found.isPresent());
        assertEquals("slow_mode", found.get().moderationState());
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "SELECT action, reason FROM live_session_moderation_events WHERE session_id = ?")) {
            ps.setObject(1, sessionId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("slow_mode", rs.getString("action"));
                assertEquals("test reason", rs.getString("reason"));
            }
        }
    }

    @Test
    void updateDvrPlaylist_setsUrl() {
        assertTrue(repo.updateDvrPlaylist(sessionId, "https://cdn.example/live/index.m3u8"));
        var found = repo.findById(sessionId);
        assertTrue(found.isPresent());
        assertEquals("https://cdn.example/live/index.m3u8", found.get().dvrPlaylistUrl());
    }
}
