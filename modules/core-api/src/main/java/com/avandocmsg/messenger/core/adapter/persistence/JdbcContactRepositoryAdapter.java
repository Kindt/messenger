package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.Contact;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JDBC adapter for {@link ContactRepositoryPort}. */
public final class JdbcContactRepositoryAdapter implements ContactRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcContactRepositoryAdapter.class);

    private final DataSource dataSource;

    public JdbcContactRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Contact> list(UserId userId) {
        var sql = """
            SELECT c.contact_user_id, u.username, u.display_name, u.phone, c.added_at
            FROM contacts c
            JOIN users u ON u.id = c.contact_user_id
            WHERE c.user_id = ?
            ORDER BY c.added_at DESC
            LIMIT ?
            """;
        var result = new ArrayList<Contact>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, userId.value());
                stmt.setInt(2, JdbcListLimits.CONTACTS);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapContact(rs));
                }
            }
            }
        } catch (Exception e) {
            log.error("Failed to list contacts for {}", userId, e);
        }
        return result;
    }

    @Override
    public boolean add(UserId userId, UserId contactUserId) {
        var sql = "INSERT INTO contacts (user_id, contact_user_id, added_at) VALUES (?, ?, now()) ON CONFLICT DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId.value());
            stmt.setObject(2, contactUserId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to add contact {} for {}", contactUserId, userId, e);
            return false;
        }
    }

    @Override
    public boolean remove(UserId userId, UserId contactUserId) {
        var sql = "DELETE FROM contacts WHERE user_id = ? AND contact_user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId.value());
            stmt.setObject(2, contactUserId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to remove contact {} for {}", contactUserId, userId, e);
            return false;
        }
    }

    @Override
    public List<UUID> findByPhoneHashes(UserId userId, List<String> phoneHashes) {
        if (phoneHashes == null || phoneHashes.isEmpty()) {
            return List.of();
        }
        var placeholders = phoneHashes.stream().map(h -> "?").collect(java.util.stream.Collectors.joining(","));
        var sql = "SELECT id FROM users WHERE phone_hash IN (" + placeholders + ") AND hidden = false AND id != ? LIMIT ?";
        var result = new ArrayList<UUID>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                for (int i = 0; i < phoneHashes.size(); i++) {
                    stmt.setString(i + 1, phoneHashes.get(i));
                }
                stmt.setObject(phoneHashes.size() + 1, userId.value());
                stmt.setInt(phoneHashes.size() + 2, JdbcListLimits.PHONE_HASH_MATCHES);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("id", UUID.class));
                }
            }
            }
        } catch (Exception e) {
            log.error("Failed to find by phone hashes", e);
        }
        return result;
    }

    private Contact mapContact(ResultSet rs) throws Exception {
        return new Contact(
            UserId.of(rs.getObject("contact_user_id", UUID.class)),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getTimestamp("added_at").toInstant()
        );
    }
}
