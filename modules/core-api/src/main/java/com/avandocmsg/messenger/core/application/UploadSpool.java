package com.avandocmsg.messenger.core.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/** Streams upload body to a temp file while computing SHA-256 (PS-1.2). */
public final class UploadSpool implements AutoCloseable {

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
            temp = Files.createTempFile("korus-upload-", ".bin");
            final MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
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
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
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
