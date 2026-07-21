package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcAdapterFailurePropagationTest {

    private static final DataSource FAILING_DATA_SOURCE = new DataSource() {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("database unavailable");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("database unavailable");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unsupported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    };

    @Test
    void isBanned_rethrowsDatabaseFailure() {
        var adapter = new JdbcChatBanAdapter(FAILING_DATA_SOURCE, null, null);

        assertThrows(IllegalStateException.class, () -> adapter.isBanned(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void insertMentions_rethrowsDatabaseFailure() {
        var adapter = new JdbcMessageMentionRepositoryAdapter(FAILING_DATA_SOURCE);

        assertThrows(IllegalStateException.class,
            () -> adapter.insertMentions(UUID.randomUUID(),
                List.of(new MessageMentionRepositoryPort.MentionRow(UUID.randomUUID(), "user"))));
    }
}
