package com.avandocmsg.messenger.api.users;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIntegrationConnectRepositoryTest {

    @Test
    void connectListAndDisconnectAreIdempotent() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:user_integration_connect_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (var conn = ds.getConnection(); var st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE user_integration_connections (
                    user_id UUID NOT NULL,
                    plugin_instance_id UUID NOT NULL,
                    connected_at TIMESTAMP NOT NULL DEFAULT now(),
                    PRIMARY KEY (user_id, plugin_instance_id)
                )
                """);
        }
        var repo = new UserIntegrationConnectRepository(ds);
        var userId = UUID.randomUUID();
        var instanceId = UUID.randomUUID();

        assertTrue(repo.connect(userId, instanceId));
        assertFalse(repo.connect(userId, instanceId));
        assertEquals(java.util.Set.of(instanceId), repo.listConnectedInstanceIds(userId));
        assertTrue(repo.disconnect(userId, instanceId));
        assertFalse(repo.disconnect(userId, instanceId));
        assertTrue(repo.listConnectedInstanceIds(userId).isEmpty());
    }
}
