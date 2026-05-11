package com.avandocmsg.messenger.api.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class FilePublicLinkRepository {
    private static final Logger log = LoggerFactory.getLogger(FilePublicLinkRepository.class);
    private final DataSource dataSource;
    private final UuidGenerator uuidGenerator;
    private final SecureRandom random = new SecureRandom();

    public FilePublicLinkRepository(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.uuidGenerator = uuidGenerator;
    }

    public static String sha256Hex(String utf8) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(utf8.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Random opaque token (hex); store only {@link #sha256Hex(String)} of token in DB. */
    public String newRawToken() {
        var b = new byte[32];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public Optional<CreatedLink> insert(UUID fileId, UUID createdBy, char kind, String passwordPlain,
                                        Instant expiresAt) {
        var rawToken = newRawToken();
        var tokenHash = sha256Hex(rawToken);
        String pwdHash = null;
        if (kind == 'C') {
            if (passwordPlain == null || passwordPlain.isBlank()) {
                return Optional.empty();
            }
            pwdHash = sha256Hex(passwordPlain);
        }
        var sql = """
            INSERT INTO file_public_links (id, file_id, created_by, link_kind, token_hash, password_hash, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        var id = uuidGenerator.randomUuid();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, fileId);
            stmt.setObject(3, createdBy);
            stmt.setString(4, String.valueOf(kind));
            stmt.setString(5, tokenHash);
            stmt.setString(6, pwdHash);
            stmt.setTimestamp(7, java.sql.Timestamp.from(expiresAt));
            stmt.executeUpdate();
            return Optional.of(new CreatedLink(id.toString(), rawToken, expiresAt));
        } catch (Exception e) {
            log.error("insert public link failed", e);
            return Optional.empty();
        }
    }

    /**
     * Отзыв публичной ссылки: помечает {@code revoked_at}; токен перестаёт проходить в {@link #findValidByTokenHash}.
     */
    public boolean revoke(UUID createdBy, UUID fileId, UUID linkId) {
        var sql = """
            UPDATE file_public_links
            SET revoked_at = now()
            WHERE id = ? AND created_by = ? AND file_id = ? AND revoked_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, linkId);
            stmt.setObject(2, createdBy);
            stmt.setObject(3, fileId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("revoke public link failed", e);
            return false;
        }
    }

    public Optional<ResolvedLink> findValidByTokenHash(String tokenHash) {
        var sql = """
            SELECT file_id, link_kind, created_by, password_hash
            FROM file_public_links
            WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ResolvedLink(
                        rs.getObject("file_id", UUID.class),
                        rs.getString("link_kind").charAt(0),
                        rs.getObject("created_by", UUID.class),
                        rs.getString("password_hash")));
                }
            }
        } catch (Exception e) {
            log.error("resolve public link failed", e);
        }
        return Optional.empty();
    }

    public record CreatedLink(String id, String rawToken, Instant expiresAt) {}

    public record ResolvedLink(UUID fileId, char linkKind, UUID createdBy, String passwordHash) {}
}
