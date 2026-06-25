package com.avandocmsg.messenger.common.jdbc;

/**
 * Shared HikariCP + PostgreSQL JDBC driver tuning (spec 025 FR-026, FR-027, FR-074).
 * Workers read env directly; core-api maps the same keys via {@code AppConfig}.
 */
public record HikariPoolSettings(
    int minimumIdle,
    long connectionTimeoutMs,
    long idleTimeoutMs,
    long maxLifetimeMs,
    long keepaliveTimeMs,
    boolean cachePrepStmts,
    int prepStmtCacheSize,
    int prepStmtCacheSqlLimit,
    int prepareThreshold
) {

  public static final int DEFAULT_MINIMUM_IDLE = 2;
  public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5_000L;
  public static final long DEFAULT_IDLE_TIMEOUT_MS = 300_000L;
  public static final long DEFAULT_MAX_LIFETIME_MS = 600_000L;
  public static final long DEFAULT_KEEPALIVE_TIME_MS = 120_000L;
  public static final boolean DEFAULT_CACHE_PREP_STMTS = true;
  public static final int DEFAULT_PREP_STMT_CACHE_SIZE = 250;
  public static final int DEFAULT_PREP_STMT_CACHE_SQL_LIMIT = 2048;
  public static final int DEFAULT_PREPARE_THRESHOLD = 1;

  public static HikariPoolSettings defaults() {
    return new HikariPoolSettings(
        DEFAULT_MINIMUM_IDLE,
        DEFAULT_CONNECTION_TIMEOUT_MS,
        DEFAULT_IDLE_TIMEOUT_MS,
        DEFAULT_MAX_LIFETIME_MS,
        DEFAULT_KEEPALIVE_TIME_MS,
        DEFAULT_CACHE_PREP_STMTS,
        DEFAULT_PREP_STMT_CACHE_SIZE,
        DEFAULT_PREP_STMT_CACHE_SQL_LIMIT,
        DEFAULT_PREPARE_THRESHOLD);
  }

  public static HikariPoolSettings fromEnv() {
    return new HikariPoolSettings(
        intEnv("DB_POOL_MINIMUM_IDLE", DEFAULT_MINIMUM_IDLE),
        longEnv("DB_POOL_CONNECTION_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS),
        longEnv("DB_POOL_IDLE_TIMEOUT_MS", DEFAULT_IDLE_TIMEOUT_MS),
        longEnv("DB_POOL_MAX_LIFETIME_MS", DEFAULT_MAX_LIFETIME_MS),
        longEnv("DB_POOL_KEEPALIVE_TIME_MS", DEFAULT_KEEPALIVE_TIME_MS),
        boolEnv("DB_JDBC_CACHE_PREP_STMTS", DEFAULT_CACHE_PREP_STMTS),
        intEnv("DB_JDBC_PREP_STMT_CACHE_SIZE", DEFAULT_PREP_STMT_CACHE_SIZE),
        intEnv("DB_JDBC_PREP_STMT_CACHE_SQL_LIMIT", DEFAULT_PREP_STMT_CACHE_SQL_LIMIT),
        intEnv("DB_JDBC_PREPARE_THRESHOLD", DEFAULT_PREPARE_THRESHOLD));
  }

  private static int intEnv(String key, int defaultValue) {
    var raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(raw.trim());
  }

  private static long longEnv(String key, long defaultValue) {
    var raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Long.parseLong(raw.trim());
  }

  private static boolean boolEnv(String key, boolean defaultValue) {
    var raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(raw.trim());
  }
}
