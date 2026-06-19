package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.admin.MigrationImportProcessor;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationImportJobRepositoryH2Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HikariDataSource ds;
    private MigrationImportJobRepository repository;
    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:mig_import_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY, org_id UUID)");
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'group',
                  owner_id UUID,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE chat_members (
                  chat_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  role VARCHAR(16) NOT NULL DEFAULT 'member',
                  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (chat_id, user_id)
                )
                """);
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  client_msg_id VARCHAR(64),
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  reply_to_msg_id UUID,
                  thread_id UUID,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  visibility_ttl_seconds INT,
                  attachment_file_id UUID,
                  voice_duration_ms INT,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  edited_at TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE migration_import_jobs (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  source VARCHAR(32) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  config_json TEXT,
                  result_json TEXT,
                  created_by UUID,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id, org_id) VALUES (?, ?)")) {
            ps.setObject(1, userId);
            ps.setObject(2, orgId);
            ps.executeUpdate();
        }
        repository = new MigrationImportJobRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insertAndProcessJob() throws Exception {
        var config = """
            {"export_json":{"name":"x","messages":[{"id":1,"type":"message","text":"hi"}]}}
            """;
        var id = repository.insert(orgId, "telegram_export_v1", config, userId);
        var chatRepo = new ChatRepository(ds, Clock.systemUTC(), UuidGenerator.standard());
        var msgPort = CoreModule.messageRepositoryPort(ds);
        var processor = new MigrationImportProcessor(
            repository, chatRepo, msgPort, UuidGenerator.standard());
        var done = processor.process(id);
        assertEquals("completed", done.orElseThrow().status());
        var result = MAPPER.readTree(done.orElseThrow().resultJson());
        assertTrue(result.path("imported_messages").asInt() >= 0);
        assertEquals("completed", repository.findById(id).orElseThrow().status());
    }
}
