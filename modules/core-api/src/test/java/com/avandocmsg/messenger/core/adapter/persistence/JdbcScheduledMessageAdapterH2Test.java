package com.avandocmsg.messenger.core.adapter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcScheduledMessageAdapterH2Test {

    private HikariDataSource ds;
    private JdbcScheduledMessageAdapter adapter;
    private UUID chatId;
    private UUID senderId;

    @BeforeEach
    void setUp() throws Exception {
        chatId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:sched_msg_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE scheduled_messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  message_type VARCHAR(32) NOT NULL,
                  content TEXT,
                  scheduled_at TIMESTAMP NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  reply_to_msg_id UUID,
                  thread_id UUID,
                  client_msg_id VARCHAR(128),
                  sent_message_id UUID,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        adapter = new JdbcScheduledMessageAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void listForSenderAndCancelPending() {
        var when = Instant.parse("2030-06-01T12:00:00Z");
        var id = adapter.create(new com.avandocmsg.messenger.core.port.ScheduledMessagePort.CreateScheduled(
            chatId, senderId, "text", "hello", when, null, null, null));
        assertTrue(id != null);

        var rows = adapter.listForSender(senderId, 10);
        assertEquals(1, rows.size());
        assertEquals("pending", rows.get(0).status());

        assertTrue(adapter.cancelPending(id, senderId));
        assertFalse(adapter.cancelPending(id, senderId));

        var after = adapter.listForSender(senderId, 10);
        assertTrue(after.isEmpty());
    }
}
