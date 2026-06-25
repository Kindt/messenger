package com.avandocmsg.messenger.common.health;

import io.nats.client.Connection;

import javax.sql.DataSource;

/** Shared NATS/JDBC probes for worker {@code /health} endpoints (spec 025 FR-138). */
public final class WorkerDependencyHealth {

    private WorkerDependencyHealth() {}

    public static boolean natsConnected(Connection connection) {
        try {
            return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean jdbcReachable(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean natsAndJdbc(Connection nats, DataSource dataSource) {
        return natsConnected(nats) && jdbcReachable(dataSource);
    }

    public static boolean natsAndOptionalJdbc(Connection nats, DataSource dataSource) {
        return natsConnected(nats) && (dataSource == null || jdbcReachable(dataSource));
    }
}
