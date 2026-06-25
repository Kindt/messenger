package com.avandocmsg.messenger.common.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Shared worker JDBC pools (same pattern as archiver / message-pipeline mains).
 */
public final class HikariDataSources {

    private HikariDataSources() {
    }

    /**
     * Returns {@code null} when {@code jdbcUrl} is null or blank.
     */
    public static HikariDataSource createOptionalPool(String jdbcUrl, String user, String password, int maxPoolSize,
                                                      String poolName) {
        return createOptionalPool(jdbcUrl, user, password, maxPoolSize, poolName, HikariPoolSettings.fromEnv());
    }

    /**
     * Returns {@code null} when {@code jdbcUrl} is null or blank.
     */
    public static HikariDataSource createOptionalPool(String jdbcUrl, String user, String password, int maxPoolSize,
                                                      String poolName, HikariPoolSettings poolSettings) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        var trimmedUrl = jdbcUrl.trim();
        var config = new HikariConfig();
        config.setJdbcUrl(trimmedUrl);
        config.setUsername(user != null ? user : "");
        config.setPassword(password != null ? password : "");
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        if (poolName != null && !poolName.isBlank()) {
            config.setPoolName(poolName);
        }
        HikariPoolSupport.apply(config, poolSettings, trimmedUrl);
        return new HikariDataSource(config);
    }

    public static void closeQuietly(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource h) {
            h.close();
        }
    }
}
