package com.avandocmsg.messenger.worker.push;

import io.nats.client.Connection;

import javax.sql.DataSource;

final class PushHealthProbe implements PushReadinessCheck {

    private final Connection natsConnection;
    private final DataSource dataSource;

    PushHealthProbe(Connection natsConnection, DataSource dataSource) {
        this.natsConnection = natsConnection;
        this.dataSource = dataSource;
    }

    @Override
    public boolean ready() {
        return natsConnected() && databaseReachable();
    }

    private boolean natsConnected() {
        try {
            return natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean databaseReachable() {
        if (dataSource == null) {
            return false;
        }
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
