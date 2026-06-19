package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.files.dto.OwnerPublicLinkSummary;
import com.avandocmsg.messenger.api.files.dto.PublicLinkSummary;
import com.avandocmsg.messenger.core.adapter.persistence.FilePublicLinkPortAdapter;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for file public link JDBC (tests and gradual migration).
 * SQL lives in {@link FilePublicLinkPortAdapter}.
 */
public class FilePublicLinkRepository {
    private final PublicLinkPort port;
    private final SecureRandom random = new SecureRandom();

    public FilePublicLinkRepository(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.port = new FilePublicLinkPortAdapter(dataSource, uuidGenerator);
    }

    FilePublicLinkRepository(PublicLinkPort port) {
        this.port = port;
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
        return port.createLink(FileId.of(fileId), UserId.of(createdBy), kind, passwordPlain, expiresAt)
            .map(c -> new CreatedLink(c.id(), c.rawToken(), c.expiresAt()));
    }

    public List<OwnerPublicLinkSummary> listActiveByOwner(UUID createdBy, int limit) {
        return port.listByOwner(UserId.of(createdBy), limit).stream()
            .map(e -> new OwnerPublicLinkSummary(e.id(), e.fileId(), e.linkKind(), e.expiresAt(), e.createdAt(),
                e.filename()))
            .toList();
    }

    public List<PublicLinkSummary> listActiveByFileAndOwner(UUID fileId, UUID createdBy) {
        return port.listByFileAndOwner(FileId.of(fileId), UserId.of(createdBy)).stream()
            .map(e -> new PublicLinkSummary(e.id(), e.linkKind(), e.expiresAt(), e.createdAt()))
            .toList();
    }

    public boolean revoke(UUID createdBy, UUID fileId, UUID linkId) {
        return port.revokeLink(UserId.of(createdBy), FileId.of(fileId), linkId);
    }

    public Optional<ResolvedLink> findValidByTokenHash(String tokenHash) {
        return port.findValidByTokenHash(tokenHash)
            .map(r -> new ResolvedLink(r.fileId(), r.linkKind(), r.createdBy(), r.passwordHash()));
    }

    public record CreatedLink(String id, String rawToken, Instant expiresAt) {}

    public record ResolvedLink(UUID fileId, char linkKind, UUID createdBy, String passwordHash) {}
}
