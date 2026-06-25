package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.nats.NatsClientSettings;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NatsConfig {
    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);
    private final Connection connection;

    public NatsConfig(AppConfig appConfig) {
        try {
            var settings = natsClientSettings(appConfig);
            var options = NatsConnectionOptions.clientBuilder(appConfig.natsUrl(), "core-api", settings).build();
            this.connection = Nats.connect(options);
            log.info(
                "NATS connected: {} (reconnectWait={}ms, connectionTimeout={}ms)",
                appConfig.natsUrl(),
                settings.reconnectWait().toMillis(),
                settings.connectionTimeout().toMillis());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Cannot connect to NATS at " + appConfig.natsUrl(), e);
        }
    }

    static NatsClientSettings natsClientSettings(AppConfig appConfig) {
        return new NatsClientSettings(
            appConfig.natsReconnectWait(),
            appConfig.natsMaxReconnects(),
            appConfig.natsConnectionTimeout(),
            appConfig.natsPingInterval());
    }

    public Connection connection() {
        return connection;
    }
}
