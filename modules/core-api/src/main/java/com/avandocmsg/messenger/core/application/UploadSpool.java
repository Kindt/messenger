package com.avandocmsg.messenger.core.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/** Streams upload body to a temp file while computing SHA-256 (PS-1.2). */
public final class UploadSpool implements AutoCloseable {

    private static final String SPOOL_DIR_NAME = "korus-upload-spool";

    private final Path path;
    private final long size;
    private final String sha256Hex;

    private UploadSpool(Path path, long size, String sha256Hex) {
        this.path = path;
        this.size = size;
        this.sha256Hex = sha256Hex;
    }

    public long size() {
        return size;
    }

    public String sha256Hex() {
        return sha256Hex;
    }

    public InputStream open() throws IOException {
        return Files.newInputStream(path);
    }

    /**
     * @param declaredSize {@code Content-Length} when known, else {@code -1}
     */
    public static Optional<UploadSpool> from(InputStream data, long declaredSize, long maxBytes) throws IOException {
        if (data == null) {
            return Optional.empty();
        }
        if (declaredSize > maxBytes) {
            return Optional.empty();
        }
        Path temp = null;
        try {
            temp = createSpoolTempFile();
            return writeAndDigest(data, declaredSize, maxBytes, temp);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    private static Optional<UploadSpool> writeAndDigest(InputStream data, long declaredSize, long maxBytes, Path temp)
        throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (var digestIn = new DigestInputStream(data, digest);
             OutputStream out = Files.newOutputStream(temp)) {
            var buf = new byte[65536];
            int n;
            while ((n = digestIn.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    return Optional.empty();
                }
                out.write(buf, 0, n);
            }
        }
        if (declaredSize >= 0 && total != declaredSize) {
            return Optional.empty();
        }
        return Optional.of(new UploadSpool(temp, total, HexFormat.of().formatHex(digest.digest())));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Prefer an owner-only spool directory under {@code java.io.tmpdir} instead of the shared temp root.
     */
    private static Path createSpoolTempFile() throws IOException {
        Path spoolDir = Path.of(System.getProperty("java.io.tmpdir"), SPOOL_DIR_NAME);
        Files.createDirectories(spoolDir);
        restrictOwnerOnlyIfPosix(spoolDir);
        Path temp = Files.createTempFile(spoolDir, "korus-upload-", ".bin");
        restrictOwnerOnlyIfPosix(temp);
        return temp;
    }

    private static void restrictOwnerOnlyIfPosix(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
            if (Files.isRegularFile(path)) {
                perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            }
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX FS (e.g. Windows): directory under tmpdir is still better than createTempFile alone
        }
    }

    @Override
    public void close() {
        deleteQuietly(path);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort temp cleanup
        }
    }
}
