package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch insert uses {@code ON CONFLICT} (PostgreSQL); H2 repo tests skip that SQL — see {@code MessageReadReceiptRepositoryH2Test}.
 */
class JdbcMessageReadReceiptAdapterBatchTest {

    @Test
    void insertBatch_usesExecuteBatchForAllRows() throws Exception {
        var ds = mock(DataSource.class);
        var conn = mock(Connection.class);
        var stmt = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeBatch()).thenReturn(new int[] {1, 1});

        var adapter = new JdbcMessageReadReceiptAdapter(ds);
        var userId = UUID.randomUUID();
        var at = Instant.parse("2025-02-01T12:00:00Z");
        var inserted = adapter.insertBatch(List.of(UUID.randomUUID(), UUID.randomUUID()), userId, at);

        assertEquals(2, inserted);
        verify(stmt, times(2)).addBatch();
        verify(stmt).executeBatch();
    }

    @Test
    void insertBatch_h2PlainInsert_insertsMultipleRows() throws Exception {
        var cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:rr_batch_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        try (var ds = new com.zaxxer.hikari.HikariDataSource(cfg)) {
            try (var c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE message_read_receipts (
                      message_id UUID NOT NULL,
                      user_id UUID NOT NULL,
                      read_at TIMESTAMP NOT NULL,
                      PRIMARY KEY (message_id, user_id)
                    )
                    """);
            }
            var userId = UUID.randomUUID();
            var msg1 = UUID.randomUUID();
            var msg2 = UUID.randomUUID();
            var at = Instant.parse("2025-02-01T12:00:00Z");
            var sql = """
                INSERT INTO message_read_receipts (message_id, user_id, read_at)
                VALUES (?, ?, ?)
                """;
            try (var conn = ds.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                for (var messageId : List.of(msg1, msg2)) {
                    stmt.setObject(1, messageId);
                    stmt.setObject(2, userId);
                    stmt.setTimestamp(3, Timestamp.from(at));
                    stmt.addBatch();
                }
                var counts = stmt.executeBatch();
                assertEquals(2, counts.length);
            }
            try (var conn = ds.getConnection();
                 var stmt = conn.prepareStatement("SELECT COUNT(*) FROM message_read_receipts");
                 var rs = stmt.executeQuery()) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
