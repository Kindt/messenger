package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConferenceRepository} без {@code INSERT … RETURNING} из {@link ConferenceRepository#insert} —
 * в H2 этот синтаксис не совпадает с PostgreSQL; строки в {@code conferences} добавляются через JDBC.
 */
class ConferenceRepositoryH2Test {

    private HikariDataSource ds;
    private ConferenceRepository repo;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:conf_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
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
                CREATE TABLE conference_participants (
                  conference_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  left_at TIMESTAMP,
                  PRIMARY KEY (conference_id, user_id)
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
                CREATE TABLE conferences (
                  id UUID NOT NULL PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  created_by UUID NOT NULL REFERENCES users(id),
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  status VARCHAR(16) NOT NULL DEFAULT 'active',
                  room_slug VARCHAR(160) NOT NULL UNIQUE,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  ended_at TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, userId);
            ps.setString(2, "alice");
            ps.setString(3, "Alice");
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
        repo = new ConferenceRepository(ds, new AppConfig(), UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void newRoomSlug_startsWithConfiguredPrefix() {
        assertTrue(repo.newRoomSlug().startsWith("avandocmsg-"));
    }

    @Test
    void findById_listForChat_endConference_afterJdbcInsert() throws Exception {
        var confId = UUID.randomUUID();
        var slug = "roomslug" + confId.toString().replace("-", "");
        insertConference(confId, chatId, userId, "Daily", "active", slug, Instant.parse("2024-03-01T10:00:00Z"), null);
        var row = repo.findById(confId).orElseThrow();
        assertEquals(confId.toString(), row.conferenceId());
        assertEquals(chatId.toString(), row.chatId());
        assertEquals("Daily", row.title());
        assertEquals("active", row.status());
        assertEquals(slug, row.roomSlug());
        assertTrue(row.joinUrl().endsWith("/" + slug));
        assertEquals(confId.toString(), repo.findActiveByRoomSlug(slug).orElseThrow().conferenceId());
        assertTrue(repo.findActiveByRoomSlug("missing").isEmpty());

        assertEquals(1, repo.listForChat(chatId, false).size());
        assertEquals(1, repo.listForChat(chatId, true).size());

        assertTrue(repo.endConference(confId));
        var ended = repo.findById(confId).orElseThrow();
        assertEquals("ended", ended.status());
        assertNotNull(ended.endedAt());
        assertTrue(repo.listForChat(chatId, true).isEmpty());
        assertEquals(1, repo.listForChat(chatId, false).size());
    }

    @Test
    void listActiveParticipants_excludesLeftUsers() throws Exception {
        var confId = UUID.randomUUID();
        var otherId = UUID.randomUUID();
        var slug = "slugp" + confId.toString().replace("-", "");
        insertConference(confId, chatId, userId, "standup", "active", slug, Instant.parse("2024-05-01T09:00:00Z"), null);
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, otherId);
            ps.setString(2, "bob");
            ps.setString(3, "Bob");
            ps.executeUpdate();
        }
        insertParticipant(confId, userId, null);
        insertParticipant(confId, otherId, java.sql.Timestamp.from(Instant.now()));
        var active = repo.listActiveParticipants(confId);
        assertEquals(1, active.size());
        assertEquals(userId.toString(), active.get(0).userId());
        assertEquals("Alice", active.get(0).displayName());
        assertEquals(1, repo.countActiveParticipants(confId));
    }

    @Test
    void findCreatorId_returnsCreatedBy() throws Exception {
        var confId = UUID.randomUUID();
        var slug = "slugc" + confId.toString().replace("-", "");
        insertConference(confId, chatId, userId, "x", "active", slug, Instant.parse("2024-04-01T00:00:00Z"), null);
        assertEquals(Optional.of(userId), repo.findCreatorId(confId));
    }

    private void insertParticipant(UUID conferenceId, UUID participantId, java.sql.Timestamp leftAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO conference_participants (conference_id, user_id, joined_at, left_at) VALUES (?, ?, CURRENT_TIMESTAMP, ?)")) {
            ps.setObject(1, conferenceId);
            ps.setObject(2, participantId);
            ps.setTimestamp(3, leftAt);
            ps.executeUpdate();
        }
    }

    private void insertConference(
        UUID id,
        UUID chat,
        UUID createdBy,
        String title,
        String status,
        String roomSlug,
        Instant createdAt,
        Instant endedAt
    ) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO conferences (id, chat_id, created_by, title, status, room_slug, created_at, ended_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            ps.setObject(1, id);
            ps.setObject(2, chat);
            ps.setObject(3, createdBy);
            ps.setString(4, title);
            ps.setString(5, status);
            ps.setString(6, roomSlug);
            ps.setTimestamp(7, java.sql.Timestamp.from(createdAt));
            if (endedAt == null) {
                ps.setTimestamp(8, null);
            } else {
                ps.setTimestamp(8, java.sql.Timestamp.from(endedAt));
            }
            ps.executeUpdate();
        }
    }
}
