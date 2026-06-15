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
            ds -> log.info("FR-OPT-09 shard pool configured (routing still primary-only scaffold)"),
            () -> log.debug("FR-OPT-09 shard pool not configured"));
    }

    /** Placeholder: org-based shard selection (always primary until Citus/2-shard ADR). */
    public static DataSource routeForOrg(DataSource primary, DataSource shard, UUID orgId) {
        if (shard == null || orgId == null) {
            return primary;
        }
        return primary;
    }
}
