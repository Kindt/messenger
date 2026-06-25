package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineFanoutLogicTest {

    private final UUID chatId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID memberA = UUID.randomUUID();
    private final UUID memberB = UUID.randomUUID();
    private final UUID bannedMember = UUID.randomUUID();

    private HikariDataSource ds;
    private com.avandocmsg.messenger.common.i18n.UserMessageSource workerMessages;

    @BeforeEach
    void initH2() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:fanout_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(10);
        ds = new HikariDataSource(cfg);
        workerMessages = WorkerMessageSources.forWorker(
            MessagePipelineWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_message_pipeline");
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE chat_members (chat_id UUID NOT NULL, user_id UUID NOT NULL, banned BOOLEAN NOT NULL DEFAULT FALSE)");
            st.execute("CREATE TABLE blocks (blocker_id UUID NOT NULL, blocked_id UUID NOT NULL, PRIMARY KEY (blocker_id, blocked_id))");
        }
        insert(chatId, senderId, false);
        insert(chatId, memberA, false);
        insert(chatId, memberB, false);
        insert(chatId, bannedMember, true);
    }

    private void insertBlock(UUID blocker, UUID blocked) throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)")) {
            ps.setObject(1, blocker);
            ps.setObject(2, blocked);
            ps.executeUpdate();
        }
    }

    private void insert(UUID c, UUID u, boolean banned) throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO chat_members (chat_id, user_id, banned) VALUES (?, ?, ?)")) {
            ps.setObject(1, c);
            ps.setObject(2, u);
            ps.setBoolean(3, banned);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void loadRecipientUserIds_excludesSenderAndBanned() {
        DataSource dataSource = ds;
        var ids = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, senderId, workerMessages);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(memberA.toString()));
        assertTrue(ids.contains(memberB.toString()));
        assertFalse(ids.contains(senderId.toString()));
        assertFalse(ids.contains(bannedMember.toString()));
    }

    @Test
    void loadRecipientUserIds_excludesRecipientBlockedWithSender() throws Exception {
        insertBlock(senderId, memberA);

        var ids = PipelineFanoutLogic.loadRecipientUserIds(ds, chatId, senderId, workerMessages);
        assertEquals(1, ids.size());
        assertTrue(ids.contains(memberB.toString()));
        assertFalse(ids.contains(memberA.toString()));
    }

    @Test
    void loadRecipientUserIds_excludesSenderBlockedByRecipient() throws Exception {
        insertBlock(memberA, senderId);

        var ids = PipelineFanoutLogic.loadRecipientUserIds(ds, chatId, senderId, workerMessages);
        assertEquals(1, ids.size());
        assertTrue(ids.contains(memberB.toString()));
        assertFalse(ids.contains(memberA.toString()));
    }

    @Test
    void loadRecipientUserIds_emptyWhenOnlySender() throws Exception {
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM chat_members");
        }
        var soloChat = UUID.randomUUID();
        insert(soloChat, senderId, false);

        assertTrue(PipelineFanoutLogic.loadRecipientUserIds(ds, soloChat, senderId, workerMessages).isEmpty());
    }

    @Test
    void loadPresenceRecipientUserIds_includesContactAndCoChatMember() throws Exception {
        var contactId = UUID.randomUUID();
        var coChatMember = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE contacts (user_id UUID NOT NULL, contact_user_id UUID NOT NULL, PRIMARY KEY (user_id, contact_user_id))");
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY, org_id UUID, hidden BOOLEAN NOT NULL DEFAULT FALSE)");
        }
        var otherChat = UUID.randomUUID();
        insert(otherChat, senderId, false);
        insert(otherChat, coChatMember, false);
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("INSERT INTO contacts (user_id, contact_user_id) VALUES (?, ?)")) {
            ps.setObject(1, senderId);
            ps.setObject(2, contactId);
            ps.executeUpdate();
        }
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("INSERT INTO users (id, hidden) VALUES (?, false)")) {
            ps.setObject(1, stranger);
            ps.executeUpdate();
        }

        var ids = PipelineFanoutLogic.loadPresenceRecipientUserIds(ds, senderId, workerMessages);
        assertTrue(ids.contains(contactId.toString()));
        assertTrue(ids.contains(coChatMember.toString()));
        assertFalse(ids.contains(senderId.toString()));
        assertFalse(ids.contains(stranger.toString()));
    }
}
