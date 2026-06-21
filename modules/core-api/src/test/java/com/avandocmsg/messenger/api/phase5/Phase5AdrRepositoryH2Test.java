package com.avandocmsg.messenger.api.phase5;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Phase5AdrRepositoryH2Test {

    private HikariDataSource ds;
    private Phase5AdrRepository repo;
    private UUID orgId;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:phase5_adr_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        repo = new Phase5AdrRepository(ds);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE organizations (id UUID PRIMARY KEY, name VARCHAR(128))");
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY, login VARCHAR(64))");
            st.execute("CREATE TABLE chats (id UUID PRIMARY KEY, title VARCHAR(256))");
            st.execute("""
                CREATE TABLE sticker_packs (
                  id UUID PRIMARY KEY, org_id UUID NOT NULL, name VARCHAR(128) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
            st.execute("""
                CREATE TABLE gif_catalog_entries (
                  id UUID PRIMARY KEY, org_id UUID, query_key VARCHAR(128) NOT NULL,
                  preview_url VARCHAR(1024), gif_url VARCHAR(1024) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
            st.execute("""
                CREATE TABLE chat_kanban_tasks (
                  id UUID PRIMARY KEY, chat_id UUID NOT NULL, column_key VARCHAR(32) NOT NULL,
                  title VARCHAR(512) NOT NULL, assignee_id UUID, created_by UUID NOT NULL,
                  sort_order INT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
            st.execute("INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'org')");
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
    void stickerPackAndGifSeed() {
        var packId = repo.createStickerPack(orgId, "Emotions");
        assertFalse(repo.listStickerPacks(orgId).isEmpty());
        repo.seedDefaultGifs(orgId);
        assertEquals(1, repo.searchGifs(orgId, "thumb").size());
        assertEquals(packId, repo.listStickerPacks(orgId).get(0).id());
    }

    @Test
    void kanbanTaskCreate() {
        var taskId = repo.createKanbanTask(chatId, userId, "todo", "Ship ADR scaffolds", null);
        assertEquals(1, repo.listKanbanTasks(chatId).size());
        assertEquals(taskId, repo.listKanbanTasks(chatId).get(0).id());
    }
}
