package com.avandocmsg.messenger.common.jdbc;

import com.zaxxer.hikari.HikariConfig;

/** Applies shared pool tuning to {@link HikariConfig} instances. */
public final class HikariPoolSupport {

  private HikariPoolSupport() {}

  public static void apply(HikariConfig config, HikariPoolSettings settings, String jdbcUrl) {
    config.setMinimumIdle(Math.max(0, settings.minimumIdle()));
    config.setConnectionTimeout(settings.connectionTimeoutMs());
    config.setIdleTimeout(settings.idleTimeoutMs());
    config.setMaxLifetime(settings.maxLifetimeMs());
    if (settings.keepaliveTimeMs() > 0) {
      config.setKeepaliveTime(settings.keepaliveTimeMs());
    }
    if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
      config.setDriverClassName("org.postgresql.Driver");
      config.addDataSourceProperty("cachePrepStmts", Boolean.toString(settings.cachePrepStmts()));
      config.addDataSourceProperty("prepStmtCacheSize", Integer.toString(settings.prepStmtCacheSize()));
      config.addDataSourceProperty("prepStmtCacheSqlLimit", Integer.toString(settings.prepStmtCacheSqlLimit()));
      config.addDataSourceProperty("prepareThreshold", Integer.toString(settings.prepareThreshold()));
    }
  }
}
