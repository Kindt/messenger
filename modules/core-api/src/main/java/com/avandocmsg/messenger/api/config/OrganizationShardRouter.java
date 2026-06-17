package com.avandocmsg.messenger.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * FR-OPT-09 phase-A scaffold: optional second shard URL via {@code DB_SHARD_JDBC_URL}.
 * When unset, routing stays on primary hot pool only.
 */
public final class OrganizationShardRouter {
    private static final Logger log = LoggerFactory.getLogger(OrganizationShardRouter.class);

    private OrganizationShardRouter() {
    }

    public static void logShardConfig(DatabaseConfig databaseConfig) {
        databaseConfig.shardDataSource().ifPresentOrElse(
            ds -> log.info("FR-OPT-09 shard pool configured (org hash routing: even=primary odd=shard)"),
            () -> log.debug("FR-OPT-09 shard pool not configured"));
    }

    /** Org-based shard selection: even hash → primary, odd → shard when configured. */
    public static DataSource routeForOrg(DataSource primary, DataSource shard, UUID orgId) {
        if (shard == null || orgId == null) {
            return primary;
        }
        return selectShard(primary, shard, orgId);
    }

    static DataSource selectShard(DataSource primary, DataSource shard, UUID orgId) {
        int bucket = Math.floorMod(orgId.hashCode(), 2);
        return bucket == 0 ? primary : shard;
    }
}
