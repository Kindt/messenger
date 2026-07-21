package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.files.dto.OwnerPublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.PublicLinkSummary;
import com.avandocmsg.messenger.core.domain.CreatedPublicLink;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.ResolvedPublicLink;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link PublicLinkPort}. */
public final class FilePublicLinkPortAdapter implements PublicLinkPort {
    private static final Logger log = LoggerFactory.getLogger(FilePublicLinkPortAdapter.class);
    private static final String COL_LINK_KIND = "link_kind";
    private final DataSource dataSource;
    private final UuidGenerator uuidGenerator;
    private final SecureRandom random = new SecureRandom();

    public FilePublicLinkPortAdapter(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public String sha256Hex(String utf8) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(utf8.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String newRawToken() {
        var b = new byte[32];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    @Override
    public Optional<CreatedPublicLink> createLink(FileId fileId, UserId createdBy, char kind,
                                                  String passwordPlain, Instant expiresAt) {
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
            stmt.setObject(2, fileId.value());
            stmt.setObject(3, createdBy.value());
            stmt.setString(4, String.valueOf(kind));
            stmt.setString(5, tokenHash);
            stmt.setString(6, pwdHash);
            stmt.setTimestamp(7, java.sql.Timestamp.from(expiresAt));
            stmt.executeUpdate();
            return Optional.of(new CreatedPublicLink(id.toString(), rawToken, expiresAt));
        } catch (Exception e) {
            log.error("insert public link failed", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean revokeLink(UserId ownerId, FileId fileId, UUID linkId) {
        var sql = """
            UPDATE file_public_links
            SET revoked_at = now()
            WHERE id = ? AND created_by = ? AND file_id = ? AND revoked_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, linkId);
            stmt.setObject(2, ownerId.value());
            stmt.setObject(3, fileId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("revoke public link failed", e);
            return false;
        }
    }

    @Override
    public List<FilePublicLinkEntry> listByFileAndOwner(FileId fileId, UserId ownerId) {
        return listActiveByFileAndOwner(fileId.value(), ownerId.value()).stream()
            .map(s -> new FilePublicLinkEntry(s.id(), s.linkKind(), s.expiresAt(), s.createdAt()))
            .toList();
    }

    @Override
    public List<OwnerPublicLinkEntry> listByOwner(UserId ownerId, int limit) {
        return listActiveByOwner(ownerId.value(), limit).stream()
            .map(s -> new OwnerPublicLinkEntry(
                s.id(), s.fileId(), s.linkKind(), s.expiresAt(), s.createdAt(), s.filename()))
            .toList();
    }

    @Override
    public Optional<ResolvedPublicLink> findValidByTokenHash(String tokenHash) {
        return findValidByTokenHashInternal(tokenHash)
            .map(r -> new ResolvedPublicLink(r.fileId(), r.linkKind(), r.createdBy(), r.passwordHash()));
    }

    private List<OwnerPublicLinkSummary> listActiveByOwner(UUID createdBy, int limit) {
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        var sql = """
            SELECT l.id, l.file_id, l.link_kind, l.expires_at, l.created_at, f.filename
            FROM file_public_links l
            LEFT JOIN file_metadata f ON f.id = l.file_id
            WHERE l.created_by = ? AND l.revoked_at IS NULL AND l.expires_at > now()
            ORDER BY l.created_at DESC
            LIMIT ?
            """;
        var result = new ArrayList<OwnerPublicLinkSummary>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, createdBy);
            stmt.setInt(2, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new OwnerPublicLinkSummary(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getObject("file_id", UUID.class).toString(),
                        rs.getString(COL_LINK_KIND),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("filename")));
                }
            }
        } catch (Exception e) {
            log.error("list owner public links failed", e);
        }
        return result;
    }

    private List<PublicLinkSummary> listActiveByFileAndOwner(UUID fileId, UUID createdBy) {
        var sql = """
            SELECT id, link_kind, expires_at, created_at
            FROM file_public_links
            WHERE file_id = ? AND created_by = ? AND revoked_at IS NULL AND expires_at > now()
            ORDER BY created_at DESC
            """;
        var result = new ArrayList<PublicLinkSummary>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, fileId);
            stmt.setObject(2, createdBy);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new PublicLinkSummary(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString(COL_LINK_KIND),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (Exception e) {
            log.error("list public links failed", e);
        }
        return result;
    }

    private Optional<ResolvedLinkRow> findValidByTokenHashInternal(String tokenHash) {
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
                    return Optional.of(new ResolvedLinkRow(
                        rs.getObject("file_id", UUID.class),
                        rs.getString(COL_LINK_KIND).charAt(0),
                        rs.getObject("created_by", UUID.class),
                        rs.getString("password_hash")));
                }
            }
        } catch (Exception e) {
            log.error("resolve public link failed", e);
        }
        return Optional.empty();
    }

    private record ResolvedLinkRow(UUID fileId, char linkKind, UUID createdBy, String passwordHash) {}
}
