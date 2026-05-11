package com.avandocmsg.messenger.worker.retention;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionHotBodyJanitorDryRunTest {

    @Test
    void dryRun_withCandidates_preparesOnlySelect_noNatsPublish() throws Exception {
        var id = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        var publishCount = new AtomicInteger();

        ResultSet rs = resultSetProxy(new boolean[] {true, false}, id, chatId, senderId);
        var setQueryTimeoutCalls = new AtomicInteger();
        PreparedStatement ps = preparedStatementProxy(rs, setQueryTimeoutCalls);
        var jdbcConn = connectionProxy(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);
        Connection nats = natsProxy(publishCount);

        var platform = new RetentionPlatformDefaults(null, true, false);
        int cleared = RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "retention/body/",
            platform,
            25,
            false,
            true,
            true,
            0,
            false,
            "b",
            0,
            0,
            0L,
            Long.MAX_VALUE,
            true,
            "",
            false
        );

        assertEquals(0, cleared);
        assertEquals(0, publishCount.get());
        assertEquals(0, setQueryTimeoutCalls.get());
        assertEquals(1, prepareCount.get());
        assertTrue(capturedSql.get().contains("FROM messages"));
        assertTrue(capturedSql.get().contains("SELECT"));
    }

    @Test
    void dryRun_emptyBatch_singlePrepareStatement() throws Exception {
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        var publishCount = new AtomicInteger();
        ResultSet rs = resultSetProxy(new boolean[] {false}, null, null, null);
        PreparedStatement ps = preparedStatementProxy(rs, null);
        var jdbcConn = connectionProxy(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);
        Connection nats = natsProxy(publishCount);

        var platform = new RetentionPlatformDefaults(null, true, false);
        int cleared = RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "p/",
            platform,
            10,
            false,
            true,
            true,
            1,
            false,
            "b",
            0,
            0,
            0L,
            Long.MAX_VALUE,
            true,
            "",
            false
        );
        assertEquals(0, cleared);
        assertEquals(0, publishCount.get());
        assertEquals(1, prepareCount.get());
        assertTrue(capturedSql.get().contains("SELECT"));
    }

    @Test
    void dryRun_withPositiveJdbcQueryTimeout_invokesSetQueryTimeoutOnSelect() throws Exception {
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        var publishCount = new AtomicInteger();
        var setQueryTimeoutCalls = new AtomicInteger();
        var lastTimeoutSeconds = new AtomicInteger(-1);

        ResultSet rs = resultSetProxy(new boolean[] {false}, null, null, null);
        PreparedStatement ps = preparedStatementProxy(rs, setQueryTimeoutCalls, lastTimeoutSeconds);
        var jdbcConn = connectionProxy(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);
        Connection nats = natsProxy(publishCount);

        var platform = new RetentionPlatformDefaults(null, true, false);
        RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "p/",
            platform,
            10,
            false,
            true,
            true,
            0,
            false,
            "b",
            120,
            0,
            0L,
            Long.MAX_VALUE,
            true,
            "",
            false
        );

        assertEquals(1, setQueryTimeoutCalls.get());
        assertEquals(120, lastTimeoutSeconds.get());
        assertEquals(0, publishCount.get());
    }

    private static Connection natsProxy(AtomicInteger publishCount) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("publish".equals(method.getName())) {
                publishCount.incrementAndGet();
                return null;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("isClosed".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(
            RetentionHotBodyJanitorDryRunTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            h
        );
    }

    private static DataSource dataSourceProxy(Object jdbcConn) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("getConnection".equals(method.getName()) && (args == null || args.length == 0)) {
                return jdbcConn;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                return proxy;
            }
            if ("isWrapperFor".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return (DataSource) Proxy.newProxyInstance(
            RetentionHotBodyJanitorDryRunTest.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            h
        );
    }

    private static Object connectionProxy(
        PreparedStatement ps,
        AtomicInteger prepareCount,
        AtomicReference<String> capturedSql
    ) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length >= 1
                && args[0] instanceof String sql) {
                prepareCount.incrementAndGet();
                capturedSql.set(sql);
                return ps;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("isClosed".equals(method.getName())) {
                return false;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                return proxy;
            }
            if ("isWrapperFor".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return Proxy.newProxyInstance(
            RetentionHotBodyJanitorDryRunTest.class.getClassLoader(),
            new Class<?>[] {java.sql.Connection.class},
            h
        );
    }

    private static PreparedStatement preparedStatementProxy(ResultSet rs, AtomicInteger setQueryTimeoutCallCount) {
        return preparedStatementProxy(rs, setQueryTimeoutCallCount, null);
    }

    private static PreparedStatement preparedStatementProxy(
        ResultSet rs,
        AtomicInteger setQueryTimeoutCallCount,
        AtomicInteger lastTimeoutSeconds
    ) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return rs;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("setQueryTimeout".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof Number n) {
                if (setQueryTimeoutCallCount != null) {
                    setQueryTimeoutCallCount.incrementAndGet();
                }
                if (lastTimeoutSeconds != null) {
                    lastTimeoutSeconds.set(n.intValue());
                }
                return null;
            }
            if ("setNull".equals(method.getName()) || "setInt".equals(method.getName()) || "setBoolean".equals(method.getName())) {
                return null;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                return proxy;
            }
            if ("isWrapperFor".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return (PreparedStatement) Proxy.newProxyInstance(
            RetentionHotBodyJanitorDryRunTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            h
        );
    }

    private static ResultSet resultSetProxy(boolean[] nextSequence, UUID id, UUID chatId, UUID senderId) {
        var idx = new AtomicInteger(-1);
        InvocationHandler h = (proxy, method, args) -> {
            if ("next".equals(method.getName())) {
                int i = idx.incrementAndGet();
                return i < nextSequence.length && nextSequence[i];
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("getObject".equals(method.getName()) && args != null && args.length >= 2 && args[0] instanceof String col) {
                if ("id".equals(col)) {
                    return id;
                }
                if ("chat_id".equals(col)) {
                    return chatId;
                }
                if ("sender_id".equals(col)) {
                    return senderId;
                }
            }
            if ("getString".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof String col) {
                if ("client_msg_id".equals(col)) {
                    return "cl";
                }
                if ("type".equals(col)) {
                    return "text";
                }
                if ("content".equals(col)) {
                    return "body";
                }
            }
            if ("getLong".equals(method.getName())) {
                return 1L;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                return proxy;
            }
            if ("isWrapperFor".equals(method.getName())) {
                return false;
            }
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(
            RetentionHotBodyJanitorDryRunTest.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            h
        );
    }
}
