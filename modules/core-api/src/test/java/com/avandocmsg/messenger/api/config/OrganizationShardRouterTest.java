package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationShardRouterTest {

    private static final DataSource PRIMARY = new StubDataSource("primary");
    private static final DataSource SHARD = new StubDataSource("shard");

    @Test
    void routeForOrg_stablePerOrg() {
        var orgId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        var first = OrganizationShardRouter.selectShard(PRIMARY, SHARD, orgId);
        assertSame(first, OrganizationShardRouter.selectShard(PRIMARY, SHARD, orgId));
        assertTrue(first == PRIMARY || first == SHARD);
    }

    @Test
    void routeForOrg_withoutShard_returnsPrimary() {
        assertSame(PRIMARY, OrganizationShardRouter.routeForOrg(PRIMARY, null, UUID.randomUUID()));
    }

    @Test
    void routeForOrg_distributesByOrgHash() {
        var orgA = UUID.fromString("00000000-0000-4000-8000-000000000000");
        var orgB = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var first = OrganizationShardRouter.selectShard(PRIMARY, SHARD, orgA);
        var second = OrganizationShardRouter.selectShard(PRIMARY, SHARD, orgB);
        // At least one org should differ from the other bucket (stable per org).
        org.junit.jupiter.api.Assertions.assertNotEquals(
            first == PRIMARY,
            second == PRIMARY);
    }

    private static final class StubDataSource implements DataSource {
        private final String name;

        private StubDataSource(String name) {
            this.name = name;
        }

        @Override
        public java.sql.Connection getConnection() {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // unused stub DataSource for routing tests
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // unused stub DataSource for routing tests
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
