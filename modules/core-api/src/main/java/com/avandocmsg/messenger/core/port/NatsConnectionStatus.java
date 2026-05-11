package com.avandocmsg.messenger.core.port;

/**
 * Для readiness: есть ли живое соединение с NATS (без публикации сообщений).
 */
public interface NatsConnectionStatus {

    boolean natsClientConnected();

    static NatsConnectionStatus always(boolean connected) {
        return () -> connected;
    }
}
