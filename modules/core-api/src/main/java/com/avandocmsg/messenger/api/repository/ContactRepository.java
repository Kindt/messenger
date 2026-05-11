package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ContactRepository {
    private static final Logger log = LoggerFactory.getLogger(ContactRepository.class);
    private final DataSource dataSource;

    public ContactRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<ContactResponse> list(UUID userId) {
        var sql = """
            SELECT c.contact_user_id, u.username, u.display_name, u.phone, c.added_at
            FROM contacts c
            JOIN users u ON u.id = c.contact_user_id
            WHERE c.user_id = ?
            ORDER BY c.added_at DESC
            """;
        var result = new ArrayList<ContactResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapContact(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list contacts for {}", userId, e);
        }
        return result;
    }

    public boolean add(UUID userId, UUID contactUserId) {
        var sql = "INSERT INTO contacts (user_id, contact_user_id, added_at) VALUES (?, ?, now()) ON CONFLICT DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, contactUserId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to add contact {} for {}", contactUserId, userId, e);
            return false;
        }
    }

    public boolean remove(UUID userId, UUID contactUserId) {
        var sql = "DELETE FROM contacts WHERE user_id = ? AND contact_user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, contactUserId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to remove contact {} for {}", contactUserId, userId, e);
            return false;
        }
    }

    public List<UUID> findByPhoneHashes(UUID userId, List<String> phoneHashes) {
        var placeholders = phoneHashes.stream().map(h -> "?").collect(java.util.stream.Collectors.joining(","));
        var sql = "SELECT id FROM users WHERE phone_hash IN (" + placeholders + ") AND hidden = false AND id != ?";
        var result = new ArrayList<UUID>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < phoneHashes.size(); i++) {
                stmt.setString(i + 1, phoneHashes.get(i));
            }
            stmt.setObject(phoneHashes.size() + 1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find by phone hashes", e);
        }
        return result;
    }

    private ContactResponse mapContact(ResultSet rs) throws Exception {
        return new ContactResponse(
            rs.getObject("contact_user_id", UUID.class).toString(),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getTimestamp("added_at").toInstant()
        );
    }
}
