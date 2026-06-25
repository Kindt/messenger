package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.jdbc.HikariDataSources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushWorkerDeviceQueryTest {

    private static final UUID CHAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = HikariDataSources.createOptionalPool(
            "jdbc:h2:mem:push_device_query_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            2,
            "push-device-query-test");
        try (var conn = dataSource.getConnection(); var st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE chat_members (
                    chat_id UUID NOT NULL,
                    user_id UUID NOT NULL
                )
                """);
            st.execute("""
                CREATE TABLE devices (
                    user_id UUID NOT NULL,
                    push_provider VARCHAR(32),
                    push_token VARCHAR(512)
                )
                """);
        }
    }

    @AfterEach
    void tearDown() {
        HikariDataSources.closeQuietly(dataSource);
    }

    @Test
    void loadTargetDevices_respectsLimit() throws Exception {
        try (var conn = dataSource.getConnection(); var st = conn.createStatement()) {
            for (int i = 0; i < 10; i++) {
                var userId = UUID.randomUUID();
                st.execute("INSERT INTO chat_members (chat_id, user_id) VALUES ('" + CHAT_ID + "', '" + userId + "')");
                st.execute("""
                    INSERT INTO devices (user_id, push_provider, push_token)
                    VALUES ('%s', 'web', 'token-%d')
                    """.formatted(userId, i));
            }
        }

        var event = new MessageWorkerEvent(
            UUID.randomUUID().toString(),
            CHAT_ID.toString(),
            SENDER_ID.toString(),
            "client-1",
            1L,
            "text",
            0,
            false,
            0,
            null,
            null);

        var devices = PushWorker.loadTargetDevices(dataSource, event, 3);
        assertEquals(3, devices.size());
    }

    @Test
    void loadTargetDevices_excludesSender() throws Exception {
        var memberId = UUID.randomUUID();
        try (var conn = dataSource.getConnection(); var st = conn.createStatement()) {
            st.execute("INSERT INTO chat_members (chat_id, user_id) VALUES ('" + CHAT_ID + "', '" + SENDER_ID + "')");
            st.execute("INSERT INTO chat_members (chat_id, user_id) VALUES ('" + CHAT_ID + "', '" + memberId + "')");
            st.execute("""
                INSERT INTO devices (user_id, push_provider, push_token)
                VALUES ('%s', 'web', 'sender-token')
                """.formatted(SENDER_ID));
            st.execute("""
                INSERT INTO devices (user_id, push_provider, push_token)
                VALUES ('%s', 'web', 'member-token')
                """.formatted(memberId));
        }

        var event = new MessageWorkerEvent(
            UUID.randomUUID().toString(),
            CHAT_ID.toString(),
            SENDER_ID.toString(),
            "client-2",
            1L,
            "text",
            0,
            false,
            0,
            null,
            null);

        var devices = PushWorker.loadTargetDevices(dataSource, event, 500);
        assertEquals(1, devices.size());
        assertEquals(memberId.toString(), devices.getFirst().userId());
    }
}
