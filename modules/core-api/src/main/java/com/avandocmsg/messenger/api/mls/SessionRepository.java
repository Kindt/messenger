package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcSessionJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for MLS session JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcSessionJdbcRepository}.
 */
public class SessionRepository {
    private final JdbcSessionJdbcRepository jdbc;

    public SessionRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcSessionJdbcRepository(dataSource, clock, uuidGenerator);
    }

    public JdbcSessionJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public Optional<MlsSession> findLatestByChatId(UUID chatId) {
        return jdbc.findLatestByChatId(chatId);
    }

    public Optional<MlsSession> findByChatId(UUID chatId, long epoch) {
        return jdbc.findByChatId(chatId, epoch);
    }

    public MlsSession create(UUID chatId, String cipherSuite) {
        return jdbc.create(chatId, cipherSuite);
    }

    public boolean advanceEpoch(UUID sessionId, byte[] treeHash, byte[] transcriptHash) {
        return jdbc.advanceEpoch(sessionId, treeHash, transcriptHash);
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
}
