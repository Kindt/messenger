package com.avandocmsg.messenger.worker.retention;

import io.nats.client.Connection;
import io.prometheus.client.CollectorRegistry;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RetentionMetrics} pass-completion gauges: updated only after candidate SELECT in {@link RetentionHotBodyJanitor#runOnce}.
 */
class RetentionHotBodyPassGaugesTest {

    private static final String EPOCH = "retention_worker_last_hot_body_pass_epoch_seconds";
    private static final String CLEARED = "retention_worker_last_pass_cleared_count";

    private static Double sample(String name) {
        return CollectorRegistry.defaultRegistry.getSampleValue(name, new String[0], new String[0]);
    }

    @Test
    void dryRun_afterPass_epochAndClearedGaugesSet() throws Exception {
        var id = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        var publishCount = new AtomicInteger();
        ResultSet rs = resultSetProxy(new boolean[] {true, false}, id, chatId, senderId);
        PreparedStatement ps = preparedStatementProxy(rs, null);
        var jdbcConn = connectionProxyForSelect(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);
        Connection nats = natsProxy(publishCount);
        long before = Instant.now().getEpochSecond();

        int cleared = RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "retention/body/",
            new RetentionPlatformDefaults(null, true, false),
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
            false,
            WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")
        );

        assertEquals(0, cleared);
        Double epoch = sample(EPOCH);
        Double clearedGauge = sample(CLEARED);
        assertNotNull(epoch);
        assertNotNull(clearedGauge);
        assertTrue(epoch >= before && epoch <= Instant.now().getEpochSecond() + 1);
        assertEquals(0.0, clearedGauge, 0.001);
    }

    @Test
    void requireMinioSkip_doesNotAdvancePassCompletionEpoch() throws Exception {
        Connection nats = natsProxy(new AtomicInteger());
        var id = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        ResultSet rs = resultSetProxy(new boolean[] {true, false}, id, chatId, senderId);
        PreparedStatement ps = preparedStatementProxy(rs, null);
        var jdbcConn = connectionProxyForSelect(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);

        RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "p/",
            new RetentionPlatformDefaults(null, true, false),
            10,
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
            false,
            WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")
        );
        Double epochAfterDryRun = sample(EPOCH);
        assertNotNull(epochAfterDryRun);

        RetentionHotBodyJanitor.runOnce(
            null,
            nats,
            null,
            false,
            "b",
            "p/",
            new RetentionPlatformDefaults(null, true, false),
            10,
            true,
            true,
            true,
            0,
            false,
            "b",
            0,
            0,
            0L,
            Long.MAX_VALUE,
            false,
            "",
            false,
            WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")
        );

        assertEquals(epochAfterDryRun, sample(EPOCH), 0.001);
    }

    @Test
    void advisoryLockNotHeld_doesNotAdvancePassCompletionEpoch() throws Exception {
        Connection nats = natsProxy(new AtomicInteger());
        var id = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var prepareCount = new AtomicInteger();
        var capturedSql = new AtomicReference<String>();
        ResultSet rs = resultSetProxy(new boolean[] {true, false}, id, chatId, senderId);
        PreparedStatement ps = preparedStatementProxy(rs, null);
        var jdbcConn = connectionProxyForSelect(ps, prepareCount, capturedSql);
        DataSource ds = dataSourceProxy(jdbcConn);

        RetentionHotBodyJanitor.runOnce(
            ds,
            nats,
            null,
            false,
            "b",
            "p/",
            new RetentionPlatformDefaults(null, true, false),
            10,
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
            false,
            WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")
        );
        Double epochAfterDryRun = sample(EPOCH);
        assertNotNull(epochAfterDryRun);

        var lockRs = lockTryResultSet(false);
        Statement lockSt = statementProxy(lockRs);
        var lockConn = connectionProxyForAdvisory(lockSt);
        DataSource lockDs = dataSourceProxy(lockConn);

        RetentionHotBodyJanitor.runOnce(
            lockDs,
            nats,
            null,
            false,
            "b",
            "p/",
            new RetentionPlatformDefaults(null, true, false),
            10,
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
            false,
            "jdbc:postgresql://localhost/hot",
            true,
            WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")
        );

        assertEquals(epochAfterDryRun, sample(EPOCH), 0.001);
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            h
        );
    }

    private static Object connectionProxyForSelect(
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {java.sql.Connection.class},
            h
        );
    }

    private static PreparedStatement preparedStatementProxy(ResultSet rs, AtomicInteger setQueryTimeoutCallCount) {
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            h
        );
    }

    private static ResultSet lockTryResultSet(boolean granted) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("next".equals(method.getName())) {
                return true;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("getBoolean".equals(method.getName()) && args != null && args.length >= 1) {
                return granted;
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            h
        );
    }

    private static Statement statementProxy(ResultSet executeQueryResult) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return executeQueryResult;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            if ("setQueryTimeout".equals(method.getName())) {
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
        return (Statement) Proxy.newProxyInstance(
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {Statement.class},
            h
        );
    }

    private static Object connectionProxyForAdvisory(Statement lockStatement) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("createStatement".equals(method.getName())) {
                return lockStatement;
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
            RetentionHotBodyPassGaugesTest.class.getClassLoader(),
            new Class<?>[] {java.sql.Connection.class},
            h
        );
    }
}
