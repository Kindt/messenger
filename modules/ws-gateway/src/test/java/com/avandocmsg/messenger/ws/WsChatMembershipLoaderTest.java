package com.avandocmsg.messenger.ws;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsChatMembershipLoaderTest {

    private HikariDataSource ds;
    private UUID userId;
    private UUID chatId;

    @BeforeEach
    void init() throws Exception {
        userId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:ws_chat_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE chat_members (chat_id UUID NOT NULL, user_id UUID NOT NULL, banned BOOLEAN NOT NULL DEFAULT FALSE)");
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chat_members (chat_id, user_id, banned) VALUES (?, ?, ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, userId);
            ps.setBoolean(3, false);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void loadChatIds_returnsActiveMemberships() {
        var loader = new WsChatMembershipLoader(ds);
        var ids = loader.loadChatIds(userId);
        assertEquals(1, ids.size());
        assertEquals(chatId.toString(), ids.getFirst());
    }

    @Test
    void loadChatIds_emptyWhenNoDataSource() {
        var loader = new WsChatMembershipLoader(null);
        assertTrue(loader.loadChatIds(userId).isEmpty());
    }
}
