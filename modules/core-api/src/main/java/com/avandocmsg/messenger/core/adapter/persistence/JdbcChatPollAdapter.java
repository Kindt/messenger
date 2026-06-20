package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.ChatPollPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcChatPollAdapter implements ChatPollPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcChatPollAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Integer>> INT_LIST = new TypeReference<>() {};

    private final DataSource dataSource;

    public JdbcChatPollAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID create(CreatePoll cmd) {
        if (cmd == null || cmd.options() == null || cmd.options().isEmpty()) {
            return null;
        }
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO chat_polls (id, chat_id, created_by, question, options, allow_multiple, closes_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, cmd.chatId());
            stmt.setObject(3, cmd.createdBy());
            stmt.setString(4, cmd.question());
            stmt.setString(5, MAPPER.writeValueAsString(cmd.options()));
            stmt.setBoolean(6, cmd.allowMultiple());
            if (cmd.closesAt() != null) {
                stmt.setTimestamp(7, Timestamp.from(cmd.closesAt()));
            } else {
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            }
            stmt.executeUpdate();
            return id;
        } catch (Exception e) {
            log.error("chat poll create failed chat={}", cmd.chatId(), e);
            return null;
        }
    }

    @Override
    public Optional<PollRow> find(UUID pollId) {
        var sql = """
            SELECT id, chat_id, created_by, question, options, allow_multiple, closes_at, created_at
            FROM chat_polls WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, pollId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPoll(rs));
                }
            }
        } catch (Exception e) {
            log.error("chat poll find failed {}", pollId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<PollRow> listForChat(UUID chatId, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, chat_id, created_by, question, options, allow_multiple, closes_at, created_at
            FROM chat_polls WHERE chat_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setInt(2, lim);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<PollRow>();
                while (rs.next()) {
                    out.add(mapPoll(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("chat poll list failed chat={}", chatId, e);
            return List.of();
        }
    }

    @Override
    public boolean vote(UUID pollId, UUID userId, List<Integer> optionIndexes) {
        if (optionIndexes == null || optionIndexes.isEmpty()) {
            return false;
        }
        var insertSql = """
            INSERT INTO chat_poll_votes (poll_id, user_id, option_indexes, voted_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(insertSql)) {
            stmt.setObject(1, pollId);
            stmt.setObject(2, userId);
            stmt.setString(3, MAPPER.writeValueAsString(optionIndexes));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (!isUniqueViolation(e)) {
                log.error("chat poll vote insert failed poll={} user={}", pollId, userId, e);
                return false;
            }
        } catch (Exception e) {
            log.error("chat poll vote insert failed poll={} user={}", pollId, userId, e);
            return false;
        }
        return updateVote(pollId, userId, optionIndexes);
    }

    private boolean updateVote(UUID pollId, UUID userId, List<Integer> optionIndexes) {
        var updateSql = """
            UPDATE chat_poll_votes SET option_indexes = ?, voted_at = CURRENT_TIMESTAMP
            WHERE poll_id = ? AND user_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, MAPPER.writeValueAsString(optionIndexes));
            stmt.setObject(2, pollId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("chat poll vote update failed poll={} user={}", pollId, userId, e);
            return false;
        }
    }

    @Override
    public List<PollRow> listDue(Instant now, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, chat_id, created_by, question, options, allow_multiple, closes_at, created_at
            FROM chat_polls
            WHERE closes_at IS NOT NULL AND closes_at <= ?
            ORDER BY closes_at ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(now));
            stmt.setInt(2, lim);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<PollRow>();
                while (rs.next()) {
                    out.add(mapPoll(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("chat poll listDue failed", e);
            return List.of();
        }
    }

    @Override
    public boolean updateStatus(UUID pollId, String status) {
        return false;
    }

    @Override
    public List<VoteRow> listVotes(UUID pollId) {
        var sql = """
            SELECT poll_id, user_id, option_indexes, voted_at
            FROM chat_poll_votes WHERE poll_id = ?
            ORDER BY voted_at ASC
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, pollId);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<VoteRow>();
                while (rs.next()) {
                    out.add(mapVote(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("chat poll listVotes failed poll={}", pollId, e);
            return List.of();
        }
    }

    private static PollRow mapPoll(java.sql.ResultSet rs) throws Exception {
        var closes = rs.getTimestamp("closes_at");
        var created = rs.getTimestamp("created_at");
        return new PollRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            rs.getString("question"),
            parseStringList(rs.getString("options")),
            rs.getBoolean("allow_multiple"),
            closes != null ? closes.toInstant() : null,
            created != null ? created.toInstant() : null);
    }

    private static VoteRow mapVote(java.sql.ResultSet rs) throws Exception {
        var voted = rs.getTimestamp("voted_at");
        return new VoteRow(
            rs.getObject("poll_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            parseIntList(rs.getString("option_indexes")),
            voted != null ? voted.toInstant() : null);
    }

    private static List<String> parseStringList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(json, STRING_LIST);
    }

    private static List<Integer> parseIntList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(json, INT_LIST);
    }

    private static boolean isUniqueViolation(SQLException e) {
        var state = e.getSQLState();
        if (state != null && (state.startsWith("23") || "23505".equals(state))) {
            return true;
        }
        var msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("unique");
    }
}
