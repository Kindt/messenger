package com.avandocmsg.messenger.api.mls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class SessionRepository {
    private static final Logger log = LoggerFactory.getLogger(SessionRepository.class);
    private final DataSource dataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public SessionRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    public Optional<MlsSession> findLatestByChatId(UUID chatId) {
        var sql = """
            SELECT id, chat_id, epoch, cipher_suite, tree_hash, confirmed_transcript_hash, group_context, created_at, updated_at
            FROM e2ee_sessions WHERE chat_id = ? ORDER BY epoch DESC LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find latest session for chat {}", chatId, e);
        }
        return Optional.empty();
    }

    public Optional<MlsSession> findByChatId(UUID chatId, long epoch) {
        var sql = "SELECT id, chat_id, epoch, cipher_suite, tree_hash, confirmed_transcript_hash, group_context, created_at, updated_at " +
                  "FROM e2ee_sessions WHERE chat_id = ? AND epoch = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setLong(2, epoch);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find session for chat {} epoch {}", chatId, epoch, e);
        }
        return Optional.empty();
    }

    public MlsSession create(UUID chatId, String cipherSuite) {
        var sql = "INSERT INTO e2ee_sessions (id, chat_id, epoch, cipher_suite, created_at, updated_at) VALUES (?, ?, 0, ?, now(), now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            var id = uuidGenerator.randomUuid();
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setString(3, cipherSuite);
            stmt.executeUpdate();
            var now = clock.instant();
            return new MlsSession(id, chatId, 0, cipherSuite, null, null, null, now, now);
        } catch (Exception e) {
            log.error("Failed to create MLS session for chat {}", chatId, e);
            return null;
        }
    }

    public boolean advanceEpoch(UUID sessionId, byte[] treeHash, byte[] transcriptHash) {
        var sql = "UPDATE e2ee_sessions SET epoch = epoch + 1, tree_hash = ?, confirmed_transcript_hash = ?, updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBytes(1, treeHash);
            stmt.setBytes(2, transcriptHash);
            stmt.setObject(3, sessionId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to advance epoch for session {}", sessionId, e);
            return false;
        }
    }

    public record MlsSession(
        UUID id,
        UUID chatId,
        long epoch,
        String cipherSuite,
        byte[] treeHash,
        byte[] confirmedTranscriptHash,
        String groupContext,
        Instant createdAt,
        Instant updatedAt
    ) {}

    private MlsSession mapSession(java.sql.ResultSet rs) throws Exception {
        return new MlsSession(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getLong("epoch"),
            rs.getString("cipher_suite"),
            rs.getBytes("tree_hash"),
            rs.getBytes("confirmed_transcript_hash"),
            rs.getString("group_context"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
