package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.messages.MessageService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcFileMetadataAdapter;
import com.avandocmsg.messenger.core.adapter.storage.FileProxyObjectStorageAdapter;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AdminExportComplianceSeedH2Test {

    private HikariDataSource ds;
    private UUID chatId;
    private UUID actorId;
    private AdminExportComplianceSeed seed;
    private MemoryFileProxy fileProxy;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        fileProxy = new MemoryFileProxy();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:export_prep_" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'group',
                  owner_id UUID REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE chat_members (
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  user_id UUID NOT NULL REFERENCES users(id),
                  role VARCHAR(16) NOT NULL DEFAULT 'member',
                  banned BOOLEAN NOT NULL DEFAULT false,
                  muted BOOLEAN NOT NULL DEFAULT false,
                  personal_filter_active BOOLEAN NOT NULL DEFAULT false,
                  PRIMARY KEY (chat_id, user_id)
                )
                """);
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  sender_id UUID NOT NULL REFERENCES users(id),
                  client_msg_id VARCHAR(64),
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  reply_to_msg_id UUID,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  visibility_ttl_seconds INT,
                  attachment_file_id UUID,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  edited_at TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE file_metadata (
                  id UUID PRIMARY KEY,
                  filename VARCHAR(512) NOT NULL DEFAULT '',
                  mime_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
                  size BIGINT NOT NULL DEFAULT 0,
                  uploaded_by UUID NOT NULL REFERENCES users(id),
                  content_hash VARCHAR(64),
                  storage_key VARCHAR(512),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE file_blob (
                  content_hash VARCHAR(64) PRIMARY KEY,
                  storage_key VARCHAR(512) NOT NULL,
                  blob_size BIGINT NOT NULL DEFAULT 0,
                  ref_count INT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE chat_retention_policy (
                  chat_id UUID PRIMARY KEY REFERENCES chats(id) ON DELETE CASCADE,
                  hot_message_body_max_age_days INT,
                  hot_metadata_min_age_days INT,
                  archive_metadata_enabled BOOLEAN NOT NULL DEFAULT true,
                  deep_archive_enabled BOOLEAN NOT NULL DEFAULT true,
                  legal_hold BOOLEAN NOT NULL DEFAULT false,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_by UUID REFERENCES users(id) ON DELETE SET NULL
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, actorId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO chats (id, title, type, owner_id) VALUES (?, 'smoke', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, actorId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO chat_members (chat_id, user_id, role) VALUES (?, ?, 'owner')")) {
            ps.setObject(1, chatId);
            ps.setObject(2, actorId);
            ps.executeUpdate();
        }

        var clock = java.time.Clock.systemUTC();
        var uuidGen = UuidGenerator.standard();
        var chatRepository = new ChatRepository(ds, clock, uuidGen);
        var messageRepository = new MessageRepository(ds, clock);
        var appConfig = new AppConfig() {
            @Override
            public long mediaMaxUploadBytes() {
                return 10_000_000L;
            }
        };
        var fileApplicationService = new FileApplicationService(
            new JdbcFileMetadataAdapter(ds),
            messageRepository,
            new FileProxyObjectStorageAdapter(fileProxy),
            uuidGen,
            appConfig.mediaMaxUploadBytes(),
            false);
        var fileService = new FileService(fileApplicationService, messageRepository);
        var messageService = new MessageService(
            messageRepository,
            chatRepository,
            new BlockRepository(ds),
            mock(MlsService.class),
            mock(NatsOutboundPort.class),
            uuidGen
        );
        seed = new AdminExportComplianceSeed(
            null,
            messageService,
            fileService,
            chatRepository,
            new ChatRetentionPolicyRepository(ds)
        );
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void prepare_includeFile_persistsMetadataAndFileMessage() throws Exception {
        var result = seed.prepare(
            actorId,
            new AdminExportCompliancePrepRequest(chatId.toString(), false, 2, true, "h2-smoke.txt")
        );

        assertEquals(chatId.toString(), result.response().chatId());
        assertTrue(result.response().retentionPatched());
        assertNotNull(result.response().fileId());
        assertNotNull(result.response().fileMessageId());
        assertEquals(3, result.response().messageIds().size());

        var fileId = UUID.fromString(result.response().fileId());
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("SELECT filename FROM file_metadata WHERE id = ?")) {
            ps.setObject(1, fileId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("h2-smoke.txt", rs.getString(1));
            }
        }

        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM messages WHERE chat_id = ? AND type = 'file' AND attachment_file_id = ?")) {
            ps.setObject(1, chatId);
            ps.setObject(2, fileId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        assertTrue(fileProxy.hasObject(fileId + "/h2-smoke.txt"));
    }
}
