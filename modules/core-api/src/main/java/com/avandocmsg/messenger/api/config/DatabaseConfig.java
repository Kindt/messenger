package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.jdbc.HikariPoolSettings;
import com.avandocmsg.messenger.common.jdbc.HikariPoolSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;

public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private final AppConfig appConfig;

    public DatabaseConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public DataSource dataSource() {
        return buildPool(appConfig.dbJdbcUrl(), "avandocmsg-hot", appConfig.dbPoolSize());
    }

    /** Optional read replica pool (spec 006 FR-OPT-05). Falls back to primary URL when unset. */
    public Optional<DataSource> readDataSource() {
        var url = appConfig.dbReadJdbcUrl();
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        var poolSize = Math.max(2, appConfig.dbReadPoolSize());
        return Optional.of(buildPool(url, "avandocmsg-hot-read", poolSize));
    }

    /** Optional shard pool (FR-OPT-09 phase A scaffold). */
    public Optional<DataSource> shardDataSource() {
        var url = appConfig.dbShardJdbcUrl();
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        var poolSize = Math.max(2, appConfig.dbReadPoolSize());
        return Optional.of(buildPool(url, "avandocmsg-shard-0", poolSize));
    }

    public void warnIfPoolOversubscribed() {
        var replicas = appConfig.apiReplicas();
        var pool = appConfig.dbPoolSize();
        var maxConn = appConfig.postgresMaxConnections();
        var needed = replicas * pool;
        var threshold = (int) (maxConn * 0.8);
        if (needed > threshold) {
            log.warn(
                "DB pool may exhaust postgres max_connections: api_replicas={} x db.pool.size={} = {} > 80% of {} ({}). "
                    + "Lower DB_POOL_SIZE or raise POSTGRES_MAX_CONNECTIONS.",
                replicas, pool, needed, maxConn, threshold);
        }
        if (appConfig.dbReadJdbcUrl() != null && !appConfig.dbReadJdbcUrl().isBlank()) {
            log.info("Read replica JDBC configured: {}", appConfig.dbReadJdbcUrl());
        }
    }

    HikariPoolSettings poolSettings() {
        return new HikariPoolSettings(
            appConfig.dbPoolMinimumIdle(),
            appConfig.dbPoolConnectionTimeoutMs(),
            appConfig.dbPoolIdleTimeoutMs(),
            appConfig.dbPoolMaxLifetimeMs(),
            appConfig.dbPoolKeepaliveTimeMs(),
            appConfig.dbJdbcCachePrepStmts(),
            appConfig.dbJdbcPrepStmtCacheSize(),
            appConfig.dbJdbcPrepStmtCacheSqlLimit(),
            appConfig.dbJdbcPrepareThreshold());
    }

    private HikariDataSource buildPool(String jdbcUrl, String poolName, int maxPoolSize) {
        var hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(appConfig.dbUser());
        hikari.setPassword(appConfig.dbPassword());
        hikari.setMaximumPoolSize(maxPoolSize);
        hikari.setPoolName(poolName);
        hikari.setReadOnly(poolName.contains("-read"));
        HikariPoolSupport.apply(hikari, poolSettings(), jdbcUrl);
        var ds = new HikariDataSource(hikari);
        log.info(
            "Database pool configured: {} (max={}, prepareThreshold={})",
            jdbcUrl,
            maxPoolSize,
            poolSettings().prepareThreshold());
        return ds;
    }
}
