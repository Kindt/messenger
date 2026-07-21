package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcSessionJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
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
    ) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MlsSession that)) {
                return false;
            }
            return epoch == that.epoch
                && Objects.equals(id, that.id)
                && Objects.equals(chatId, that.chatId)
                && Objects.equals(cipherSuite, that.cipherSuite)
                && Arrays.equals(treeHash, that.treeHash)
                && Arrays.equals(confirmedTranscriptHash, that.confirmedTranscriptHash)
                && Objects.equals(groupContext, that.groupContext)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                id, chatId, epoch, cipherSuite,
                Arrays.hashCode(treeHash), Arrays.hashCode(confirmedTranscriptHash),
                groupContext, createdAt, updatedAt);
        }

        @Override
        public String toString() {
            return "MlsSession[id=" + id
                + ", chatId=" + chatId
                + ", epoch=" + epoch
                + ", cipherSuite=" + cipherSuite
                + ", treeHash=" + Arrays.toString(treeHash)
                + ", confirmedTranscriptHash=" + Arrays.toString(confirmedTranscriptHash)
                + ", groupContext=" + groupContext
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "]";
        }
    }
}
