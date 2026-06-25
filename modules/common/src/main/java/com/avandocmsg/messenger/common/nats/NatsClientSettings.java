package com.avandocmsg.messenger.common.nats;

import java.time.Duration;

/** NATS jnats client tuning (spec 025 FR-028). Env keys mirror core-api {@code application.properties}. */
public record NatsClientSettings(
    Duration reconnectWait,
    int maxReconnects,
    Duration connectionTimeout,
    Duration pingInterval
) {

  public static final Duration DEFAULT_RECONNECT_WAIT = Duration.ofSeconds(2);
  public static final int DEFAULT_MAX_RECONNECTS = -1;
  public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_PING_INTERVAL = Duration.ofMinutes(2);

  public static NatsClientSettings defaults() {
    return new NatsClientSettings(
        DEFAULT_RECONNECT_WAIT,
        DEFAULT_MAX_RECONNECTS,
        DEFAULT_CONNECTION_TIMEOUT,
        DEFAULT_PING_INTERVAL);
  }

  public static NatsClientSettings fromEnv() {
    return new NatsClientSettings(
        durationMsEnv("NATS_RECONNECT_WAIT_MS", DEFAULT_RECONNECT_WAIT),
        intEnv("NATS_MAX_RECONNECTS", DEFAULT_MAX_RECONNECTS),
        durationMsEnv("NATS_CONNECTION_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT),
        durationMsEnv("NATS_PING_INTERVAL_MS", DEFAULT_PING_INTERVAL));
  }

  private static Duration durationMsEnv(String key, Duration defaultValue) {
    var raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Duration.ofMillis(Long.parseLong(raw.trim()));
  }

  private static int intEnv(String key, int defaultValue) {
    var raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(raw.trim());
  }
}
