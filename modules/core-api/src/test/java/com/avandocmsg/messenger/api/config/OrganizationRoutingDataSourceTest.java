package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;

class OrganizationRoutingDataSourceTest {

    @AfterEach
    void tearDown() {
        OrgRoutingContext.clear();
    }

    @Test
    void routesToPrimaryWhenOrgContextMissing() throws SQLException {
        var primary = stub("primary");
        var shard = stub("shard");
        var routing = new OrganizationRoutingDataSource(primary, shard);
        try (Connection ignored = routing.getConnection()) {
            assertSame(primary, primaryRef.get());
        }
    }

    @Test
    void routesByOrgContextWhenShardConfigured() throws SQLException {
        var primary = stub("primary");
        var shard = stub("shard");
        var routing = new OrganizationRoutingDataSource(primary, shard);
        var orgA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OrgRoutingContext.set(orgA);
        try (Connection ignored = routing.getConnection()) {
            var expected = OrganizationShardRouter.selectShard(primary, shard, orgA);
            assertSame(expected, primaryRef.get());
        }
    }

    private final AtomicReference<DataSource> primaryRef = new AtomicReference<>();

    private DataSource stub(String label) {
        return new DataSource() {
            @Override
            public Connection getConnection() {
                primaryRef.set(this);
                return new StubConnection(label);
            }

            @Override
            public Connection getConnection(String username, String password) {
                return getConnection();
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
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
            public <T> T unwrap(Class<T> iface) {
                return null;
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    private static final class StubConnection implements Connection {
        private final String label;

        StubConnection(String label) {
            this.label = label;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public java.sql.Statement createStatement() {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public String nativeSQL(String sql) {
            return sql;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {
        }

        @Override
        public boolean getAutoCommit() {
            return true;
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public java.sql.DatabaseMetaData getMetaData() {
            return null;
        }

        @Override
        public void setReadOnly(boolean readOnly) {
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public void setCatalog(String catalog) {
        }

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public void setTransactionIsolation(int level) {
        }

        @Override
        public int getTransactionIsolation() {
            return Connection.TRANSACTION_READ_COMMITTED;
        }

        @Override
        public java.sql.SQLWarning getWarnings() {
            return null;
        }

        @Override
        public void clearWarnings() {
        }

        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.util.Map<String, Class<?>> getTypeMap() {
            return java.util.Map.of();
        }

        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) {
        }

        @Override
        public void setHoldability(int holdability) {
        }

        @Override
        public int getHoldability() {
            return 0;
        }

        @Override
        public java.sql.Savepoint setSavepoint() {
            return null;
        }

        @Override
        public java.sql.Savepoint setSavepoint(String name) {
            return null;
        }

        @Override
        public void rollback(java.sql.Savepoint savepoint) {
        }

        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) {
        }

        @Override
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) {
            throw new UnsupportedOperationException(label);
        }

        @Override
        public java.sql.Clob createClob() {
            return null;
        }

        @Override
        public java.sql.Blob createBlob() {
            return null;
        }

        @Override
        public java.sql.NClob createNClob() {
            return null;
        }

        @Override
        public java.sql.SQLXML createSQLXML() {
            return null;
        }

        @Override
        public boolean isValid(int timeout) {
            return true;
        }

        @Override
        public void setClientInfo(String name, String value) {
        }

        @Override
        public void setClientInfo(java.util.Properties properties) {
        }

        @Override
        public String getClientInfo(String name) {
            return null;
        }

        @Override
        public java.util.Properties getClientInfo() {
            return new java.util.Properties();
        }

        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) {
            return null;
        }

        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) {
            return null;
        }

        @Override
        public void setSchema(String schema) {
        }

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public void abort(java.util.concurrent.Executor executor) {
        }

        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {
        }

        @Override
        public int getNetworkTimeout() {
            return 0;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
