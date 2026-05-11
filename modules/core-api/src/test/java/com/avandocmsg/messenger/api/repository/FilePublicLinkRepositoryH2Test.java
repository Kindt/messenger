package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FilePublicLinkRepositoryH2Test {

    private HikariDataSource ds;
    private FilePublicLinkRepository repo;
    private UUID fileId;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        fileId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:fplink_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("CREATE TABLE file_metadata (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE file_public_links (
                  id UUID PRIMARY KEY,
                  file_id UUID NOT NULL REFERENCES file_metadata(id),
                  created_by UUID NOT NULL REFERENCES users(id),
                  link_kind CHAR(1) NOT NULL,
                  token_hash VARCHAR(64) NOT NULL,
                  password_hash VARCHAR(128),
                  expires_at TIMESTAMP NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  revoked_at TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO file_metadata (id) VALUES (?)")) {
            ps.setObject(1, fileId);
            ps.executeUpdate();
        }
        repo = new FilePublicLinkRepository(ds, UuidGenerator.standard());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insert_kindC_requiresPassword() {
        var exp = Instant.now().plus(1, ChronoUnit.HOURS);
        assertTrue(repo.insert(fileId, userId, 'C', null, exp).isEmpty());
        assertTrue(repo.insert(fileId, userId, 'C', "   ", exp).isEmpty());
    }

    @Test
    void insert_kindB_then_findValidByTokenHash() {
        var exp = Instant.now().plus(1, ChronoUnit.HOURS);
        var created = repo.insert(fileId, userId, 'B', null, exp).orElseThrow();
        var hash = FilePublicLinkRepository.sha256Hex(created.rawToken());
        var resolved = repo.findValidByTokenHash(hash).orElseThrow();
        assertEquals(fileId, resolved.fileId());
        assertEquals('B', resolved.linkKind());
        assertEquals(userId, resolved.createdBy());
        assertNull(resolved.passwordHash());
    }

    @Test
    void insert_kindC_withPassword_resolvesWithPasswordHash() {
        var exp = Instant.now().plus(1, ChronoUnit.HOURS);
        var pwd = "p4ss!";
        var created = repo.insert(fileId, userId, 'C', pwd, exp).orElseThrow();
        var hash = FilePublicLinkRepository.sha256Hex(created.rawToken());
        var resolved = repo.findValidByTokenHash(hash).orElseThrow();
        assertEquals('C', resolved.linkKind());
        assertEquals(FilePublicLinkRepository.sha256Hex(pwd), resolved.passwordHash());
    }

    @Test
    void findValidByTokenHash_emptyWhenExpired() {
        var exp = Instant.now().minus(1, ChronoUnit.HOURS);
        var created = repo.insert(fileId, userId, 'B', null, exp).orElseThrow();
        var hash = FilePublicLinkRepository.sha256Hex(created.rawToken());
        assertTrue(repo.findValidByTokenHash(hash).isEmpty());
    }

    @Test
    void revoke_then_findValidEmpty() {
        var exp = Instant.now().plus(1, ChronoUnit.HOURS);
        var created = repo.insert(fileId, userId, 'A', null, exp).orElseThrow();
        var linkId = UUID.fromString(created.id());
        var hash = FilePublicLinkRepository.sha256Hex(created.rawToken());
        assertTrue(repo.findValidByTokenHash(hash).isPresent());
        assertTrue(repo.revoke(userId, fileId, linkId));
        assertTrue(repo.findValidByTokenHash(hash).isEmpty());
    }

    @Test
    void revoke_falseForWrongUser() {
        var exp = Instant.now().plus(1, ChronoUnit.HOURS);
        var created = repo.insert(fileId, userId, 'B', null, exp).orElseThrow();
        var linkId = UUID.fromString(created.id());
        var other = UUID.randomUUID();
        assertFalse(repo.revoke(other, fileId, linkId));
    }
}
