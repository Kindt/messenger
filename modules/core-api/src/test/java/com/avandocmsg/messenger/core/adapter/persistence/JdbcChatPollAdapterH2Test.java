package com.avandocmsg.messenger.core.adapter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcChatPollAdapterH2Test {

    private HikariDataSource ds;
    private JdbcChatPollAdapter adapter;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:chat_poll_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (id UUID PRIMARY KEY, login VARCHAR(64) NOT NULL)
                """);
            st.execute("""
                CREATE TABLE chats (id UUID PRIMARY KEY, title VARCHAR(256))
                """);
            st.execute("INSERT INTO users (id, login) VALUES ('" + userId + "', 'u1')");
            st.execute("INSERT INTO chats (id, title) VALUES ('" + chatId + "', 'c1')");
            st.execute("""
                CREATE TABLE chat_polls (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  created_by UUID NOT NULL,
                  question TEXT NOT NULL,
                  options TEXT NOT NULL,
                  allow_multiple BOOLEAN NOT NULL DEFAULT false,
                  closes_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE chat_poll_votes (
                  poll_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  option_indexes TEXT NOT NULL,
                  voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (poll_id, user_id)
                )
                """);
        }
        adapter = new JdbcChatPollAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void createVoteAndResults() {
        var pollId = adapter.create(new com.avandocmsg.messenger.core.port.ChatPollPort.CreatePoll(
            chatId, userId, "Lunch?", List.of("Pizza", "Salad"), false, null));
        assertTrue(pollId != null);

        var voter = UUID.randomUUID();
        assertTrue(adapter.vote(pollId, voter, List.of(0)));
        assertTrue(adapter.vote(pollId, voter, List.of(1)));

        var votes = adapter.listVotes(pollId);
        assertEquals(1, votes.size());
        assertEquals(List.of(1), votes.get(0).optionIndexes());

        var poll = adapter.find(pollId).orElseThrow();
        var counts = new int[poll.options().size()];
        for (var vote : adapter.listVotes(pollId)) {
            for (var idx : vote.optionIndexes()) {
                counts[idx]++;
            }
        }
        assertEquals(1, counts[1]);
        assertEquals(0, counts[0]);
    }

    @Test
    void setClosesAt() {
        var pollId = adapter.create(new com.avandocmsg.messenger.core.port.ChatPollPort.CreatePoll(
            chatId, userId, "Close?", List.of("A", "B"), false, null));
        var closedAt = java.time.Instant.parse("2020-01-01T00:00:00Z");
        assertTrue(adapter.setClosesAt(pollId, closedAt));
        var row = adapter.find(pollId).orElseThrow();
        assertEquals(closedAt, row.closesAt());
    }
}
