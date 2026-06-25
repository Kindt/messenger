package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.jdbc.HikariPoolSettings;
import com.avandocmsg.messenger.common.jdbc.HikariPoolSupport;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseConfigTest {

    @Test
    void poolSettings_defaultPrepareThresholdIsOne() {
        assumeTrue(System.getenv("DB_JDBC_PREPARE_THRESHOLD") == null);
        var cfg = new AppConfig();
        var db = new DatabaseConfig(cfg);
        assertEquals(1, db.poolSettings().prepareThreshold());
    }

    @Test
    void hikariApply_setsPostgresPrepareThresholdFromAppConfig() {
        var appConfig = new AppConfig() {
            @Override
            public int dbJdbcPrepareThreshold() {
                return 3;
            }

            @Override
            public int dbJdbcPrepStmtCacheSize() {
                return 128;
            }
        };
        var hikari = new HikariConfig();
        HikariPoolSupport.apply(hikari, new DatabaseConfig(appConfig).poolSettings(), "jdbc:postgresql://localhost/db");
        assertEquals("3", hikari.getDataSourceProperties().getProperty("prepareThreshold"));
        assertEquals("128", hikari.getDataSourceProperties().getProperty("prepStmtCacheSize"));
    }

    @Test
    void poolSettings_matchHikariPoolSettingsDefaults() {
        assumeTrue(System.getenv("DB_POOL_MINIMUM_IDLE") == null);
        assumeTrue(System.getenv("DB_JDBC_PREPARE_THRESHOLD") == null);
        var defaults = HikariPoolSettings.defaults();
        var actual = new DatabaseConfig(new AppConfig()).poolSettings();
        assertEquals(defaults.minimumIdle(), actual.minimumIdle());
        assertEquals(defaults.connectionTimeoutMs(), actual.connectionTimeoutMs());
        assertEquals(defaults.prepareThreshold(), actual.prepareThreshold());
    }
}
