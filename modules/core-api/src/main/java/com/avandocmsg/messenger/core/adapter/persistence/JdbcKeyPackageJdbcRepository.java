package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.crypto.dto.KeyPackageResponse;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcKeyPackageJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcKeyPackageJdbcRepository.class);
    private final DataSource dataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public JdbcKeyPackageJdbcRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    public KeyPackageResponse insert(UUID userId, byte[] publicKey, byte[] signatureKey,
                                     String cipherSuite, String protocolVersion) {
        if (dataSource == null) {
            return null;
        }
        var sql = "INSERT INTO e2ee_key_packages (id, user_id, public_key, signature_key, cipher_suite, protocol_version, created_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            var id = uuidGenerator.randomUuid();
            stmt.setObject(1, id);
            stmt.setObject(2, userId);
            stmt.setBytes(3, publicKey);
            stmt.setBytes(4, signatureKey);
            stmt.setString(5, cipherSuite);
            stmt.setString(6, protocolVersion);
            stmt.executeUpdate();
            return new KeyPackageResponse(id.toString(), userId.toString(), publicKey, signatureKey,
                cipherSuite, protocolVersion, clock.instant());
        } catch (Exception e) {
            log.error("Failed to insert key package", e);
            return null;
        }
    }

    public List<KeyPackageResponse> findByUserId(UUID userId) {
        if (dataSource == null) {
            return List.of();
        }
        var sql = "SELECT id, user_id, public_key, signature_key, cipher_suite, protocol_version, created_at " +
                  "FROM e2ee_key_packages WHERE user_id = ? ORDER BY created_at DESC";
        var result = new ArrayList<KeyPackageResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapKeyPackage(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list key packages for user {}", userId, e);
        }
        return result;
    }

    public boolean delete(UUID id) {
        if (dataSource == null) {
            return false;
        }
        var sql = "DELETE FROM e2ee_key_packages WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to delete key package {}", id, e);
            return false;
        }
    }

    private KeyPackageResponse mapKeyPackage(java.sql.ResultSet rs) throws Exception {
        return new KeyPackageResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getObject("user_id", UUID.class).toString(),
            rs.getBytes("public_key"),
            rs.getBytes("signature_key"),
            rs.getString("cipher_suite"),
            rs.getString("protocol_version"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
