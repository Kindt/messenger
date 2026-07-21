package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditRepositoryH2Test {

    private HikariDataSource ds;
    private AuditRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:audit_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE audit_events (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  actor_user_id UUID,
                  action VARCHAR(64) NOT NULL,
                  resource_type VARCHAR(64),
                  resource_id VARCHAR(128),
                  details_json TEXT
                )
                """);
        }
        repo = new AuditRepository(ds);
        var u = UUID.randomUUID();
        repo.recordEvent(u, "other.action", "x", "1", "{}");
        repo.recordEvent(null, "message.retention.hot_body_cleared", "message", "m1", "{\"chat_id\":\"c\"}");
        repo.recordEvent(null, "message.retention.hot_body_cleared", "message", "m2", "{}");
        var passId = UUID.randomUUID().toString();
        repo.recordEvent(null, "message.retention.bulk_cleared", "retention_pass", passId, "{\"cleared\":2}");
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void listRecent_withoutFilter_returnsAllDesc() {
        var rows = repo.listRecent(10);
        assertEquals(4, rows.size());
    }

    @Test
    void listRecent_withActionFilter_returnsOnlyMatching() {
        var rows = repo.listRecent(10, "message.retention.hot_body_cleared");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> "message.retention.hot_body_cleared".equals(r.action())));
        assertTrue(rows.stream().anyMatch(r -> "m1".equals(r.resourceId())));
    }

    @Test
    void listRecent_withResourceTypeFilter_returnsMatchingActions() {
        var rows = repo.listRecent(10, null, "message");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> "message".equals(r.resourceType())));
    }

    @Test
    void listRecent_withActionAndResourceType_returnsIntersection() {
        var rows = repo.listRecent(10, "message.retention.hot_body_cleared", "message");
        assertEquals(2, rows.size());
    }

    @Test
    void listRecent_withResourceId_returnsBulkRow() {
        var bulk = repo.listRecent(10).stream()
            .filter(r -> "message.retention.bulk_cleared".equals(r.action()))
            .findFirst()
            .orElseThrow();
        var rows = repo.listRecent(10, "message.retention.bulk_cleared", "retention_pass", bulk.resourceId());
        assertEquals(1, rows.size());
        assertEquals(bulk.resourceId(), rows.get(0).resourceId());
    }

    @Test
    void listRecent_withResourceIdOnly_matchesWithoutActionOrType() {
        var rows = repo.listRecent(10, null, null, "m1");
        assertEquals(1, rows.size());
        assertEquals("m1", rows.get(0).resourceId());
        assertEquals("message.retention.hot_body_cleared", rows.get(0).action());
    }

    @Test
    void listRecent_limitZero_clampedToOne() {
        var rows = repo.listRecent(0);
        assertEquals(1, rows.size());
    }
}
