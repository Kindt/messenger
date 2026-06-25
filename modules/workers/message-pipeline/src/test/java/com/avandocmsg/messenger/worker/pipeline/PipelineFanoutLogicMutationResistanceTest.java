package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * High-assertion tests on fan-out SQL branches (spec 025 FR-164 PIT substitute on JDK 25).
 */
class PipelineFanoutLogicMutationResistanceTest {

  private HikariDataSource ds;
  private com.avandocmsg.messenger.common.i18n.UserMessageSource workerMessages;
  private final UUID chatId = UUID.randomUUID();
  private final UUID senderId = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID banned = UUID.randomUUID();

  @BeforeEach
  void initH2() throws Exception {
    var cfg = new HikariConfig();
    cfg.setJdbcUrl("jdbc:h2:mem:fanout_mut_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    cfg.setUsername("sa");
    cfg.setPassword("");
    ds = new HikariDataSource(cfg);
    workerMessages = WorkerMessageSources.forWorker(
        MessagePipelineWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_message_pipeline");
    try (var c = ds.getConnection(); var st = c.createStatement()) {
      st.execute("CREATE TABLE chat_members (chat_id UUID NOT NULL, user_id UUID NOT NULL, banned BOOLEAN NOT NULL DEFAULT FALSE)");
      st.execute("CREATE TABLE blocks (blocker_id UUID NOT NULL, blocked_id UUID NOT NULL, PRIMARY KEY (blocker_id, blocked_id))");
    }
    insertMember(chatId, senderId, false);
    insertMember(chatId, member, false);
    insertMember(chatId, banned, true);
  }

  @AfterEach
  void closeDs() {
    if (ds != null) {
      ds.close();
    }
  }

  @Test
  void loadRecipientUserIds_excludesSenderAndBanned() throws Exception {
    var ids = PipelineFanoutLogic.loadRecipientUserIds(ds, chatId, senderId, workerMessages);
    assertEquals(1, ids.size());
    assertTrue(ids.contains(member.toString()));
    assertFalse(ids.contains(senderId.toString()));
    assertFalse(ids.contains(banned.toString()));
  }

  @Test
  void loadRecipientUserIds_emptyForUnknownChat() throws Exception {
    assertTrue(PipelineFanoutLogic.loadRecipientUserIds(ds, UUID.randomUUID(), senderId, workerMessages).isEmpty());
  }

  private void insertMember(UUID chat, UUID user, boolean isBanned) throws Exception {
    try (var conn = ds.getConnection();
         var ps = conn.prepareStatement("INSERT INTO chat_members (chat_id, user_id, banned) VALUES (?, ?, ?)")) {
      ps.setObject(1, chat);
      ps.setObject(2, user);
      ps.setBoolean(3, isBanned);
      ps.executeUpdate();
    }
  }
}
