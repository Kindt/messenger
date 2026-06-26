package com.avandocmsg.messenger.api.meshcall;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshCallRecordingRepositoryH2Test {

    private HikariDataSource ds;
    private MeshCallRecordingRepository repo;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:mesh_call_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        repo = new MeshCallRecordingRepository(ds);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY, login VARCHAR(64))");
            st.execute("CREATE TABLE chats (id UUID PRIMARY KEY, title VARCHAR(256))");
            st.execute("CREATE TABLE file_metadata (id UUID PRIMARY KEY, filename VARCHAR(512), mime_type VARCHAR(128), size BIGINT, uploaded_by UUID, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            st.execute("""
                CREATE TABLE mesh_call_sessions (
                  id UUID PRIMARY KEY, chat_id UUID NOT NULL, started_by UUID NOT NULL,
                  media_mode VARCHAR(16) NOT NULL, status VARCHAR(32) NOT NULL,
                  started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, ended_at TIMESTAMP)
                """);
            st.execute("""
                CREATE TABLE mesh_call_recordings (
                  id UUID PRIMARY KEY, session_id UUID NOT NULL, chat_id UUID NOT NULL,
                  recorded_by UUID NOT NULL, kind VARCHAR(16) NOT NULL, status VARCHAR(32) NOT NULL,
                  file_id UUID, started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  ended_at TIMESTAMP, duration_ms BIGINT)
                """);
            st.execute("INSERT INTO users (id, login) VALUES ('" + userId + "', 'u')");
            st.execute("INSERT INTO chats (id, title) VALUES ('" + chatId + "', 'c')");
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void joinSessionCreatesParticipantAudit() {
        var sessionId = repo.createSession(chatId, userId, "audio");
        var otherUser = UUID.randomUUID();
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("INSERT INTO users (id, login) VALUES ('" + otherUser + "', 'u2')");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var auditInit = repo.createRecording(sessionId, chatId, userId, "audit");
        var auditJoin = repo.createRecording(sessionId, chatId, otherUser, "audit");
        assertTrue(repo.findSession(sessionId, chatId).isPresent());
        assertEquals(2, repo.listRecordings(sessionId, chatId, otherUser, true).size());
        assertNotNull(auditInit);
        assertNotNull(auditJoin);
    }

    @Test
    void sessionAuditAndUserRecordingLifecycle() {
        var sessionId = repo.createSession(chatId, userId, "video");
        var auditId = repo.createRecording(sessionId, chatId, userId, "audit");
        var userRecId = repo.createRecording(sessionId, chatId, userId, "user");
        var fileId = UUID.randomUUID();

        assertTrue(repo.findSession(sessionId, chatId).isPresent());
        assertEquals("video", repo.findSession(sessionId, chatId).get().mediaMode());

        assertTrue(repo.completeRecording(auditId, sessionId, chatId, fileId, 12_000L));
        assertTrue(repo.completeRecording(userRecId, sessionId, chatId, fileId, 5_000L));
        assertTrue(repo.endSession(sessionId, chatId));

        var userRows = repo.listRecordings(sessionId, chatId, userId, false);
        assertEquals(1, userRows.size());
        assertEquals("user", userRows.get(0).kind());
        assertEquals("completed", userRows.get(0).status());

        var allRows = repo.listRecordings(sessionId, chatId, userId, true);
        assertEquals(2, allRows.size());
        assertNotNull(allRows.stream().filter(r -> "audit".equals(r.kind())).findFirst().orElse(null));
    }
}
