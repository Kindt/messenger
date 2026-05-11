package com.avandocmsg.messenger.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private final AppConfig appConfig;

    public DatabaseConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public DataSource dataSource() {
        var hikari = new HikariConfig();
        hikari.setJdbcUrl(appConfig.dbJdbcUrl());
        hikari.setUsername(appConfig.dbUser());
        hikari.setPassword(appConfig.dbPassword());
        hikari.setMaximumPoolSize(appConfig.dbPoolSize());
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(5000);
        hikari.setIdleTimeout(300000);
        hikari.setMaxLifetime(600000);
        hikari.setPoolName("avandocmsg-hot");
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        var ds = new HikariDataSource(hikari);
        log.info("Database pool configured: {}", appConfig.dbJdbcUrl());
        return ds;
    }
}
