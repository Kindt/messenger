package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRepositoryReadRoutingTest {

    @Test
    void findById_usesReadPoolWhenConfigured() throws Exception {
        var write = new CountingDataSource("write");
        var read = new CountingDataSource("read");
        var repo = new MessageRepository(write, read, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        repo.findById(UuidGenerator.standard().randomUuid());
        assertEquals(0, write.connections.get());
        assertEquals(1, read.connections.get());
    }

    @Test
    void insert_usesWritePool() throws Exception {
        var write = new CountingDataSource("write");
        var read = new CountingDataSource("read");
        var repo = new MessageRepository(write, read, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        try {
            repo.insert(UuidGenerator.standard().randomUuid(), UuidGenerator.standard().randomUuid(),
                UuidGenerator.standard().randomUuid(), "text", "hi", null, null, null);
        } catch (Exception ignored) {
            // H2/driver not required: connection acquisition is enough
        }
        assertTrue(write.connections.get() >= 1);
        assertEquals(0, read.connections.get());
    }

    private static final class CountingDataSource implements DataSource {
        final AtomicInteger connections = new AtomicInteger();
        private final String label;

        CountingDataSource(String label) {
            this.label = label;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connections.incrementAndGet();
            throw new SQLException("no real db in unit test (" + label + ")");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        // intentional no-op: test stub
        }

        @Override
        public void setLoginTimeout(int seconds) {
        // intentional no-op: test stub
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
