package com.avandocmsg.messenger.api.config;

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
            this.connection = Nats.connect(appConfig.natsUrl());
            log.info("NATS connected: {}", appConfig.natsUrl());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Cannot connect to NATS at " + appConfig.natsUrl(), e);
        }
    }

    public Connection connection() {
        return connection;
    }
}
