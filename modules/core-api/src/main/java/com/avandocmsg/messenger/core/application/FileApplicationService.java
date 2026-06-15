package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.metrics.FileDedupMetrics;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/** Hexagonal application service for file metadata and storage (Phase 2d / US2). */
public final class FileApplicationService {
    private static final String DEDUP_PREFIX = "objects/sha256/";

    private final FileMetadataPort fileMetadataPort;
    private final MessageRepository legacyMessageRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UuidGenerator uuidGenerator;
    private final long maxUploadBytes;
    private final boolean fileDedupEnabled;

    public FileApplicationService(FileMetadataPort fileMetadataPort,
                                  MessageRepository legacyMessageRepository,
                                  ObjectStoragePort objectStoragePort,
                                  UuidGenerator uuidGenerator,
                                  long maxUploadBytes,
                                  boolean fileDedupEnabled) {
        this.fileMetadataPort = fileMetadataPort;
        this.legacyMessageRepository = legacyMessageRepository;
        this.objectStoragePort = objectStoragePort;
        this.uuidGenerator = uuidGenerator;
        this.maxUploadBytes = maxUploadBytes;
        this.fileDedupEnabled = fileDedupEnabled;
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }

    public Optional<StoredFile> findById(FileId fileId) {
        return fileMetadataPort.findById(fileId);
    }

    public Optional<StoredFile> getMetadataForUser(UserId viewerId, FileId fileId) {
        return fileMetadataPort.findById(fileId)
            .filter(file -> mayView(file, viewerId));
    }

    public Optional<FileUploadResult> upload(InputStream data, String filename, String mimeType, long size,
                                             UserId uploadedBy) {
        if (size > maxUploadBytes) {
            return Optional.empty();
        }
        try {
            var bytes = data.readAllBytes();
            if (bytes.length != size) {
                size = bytes.length;
            }
            if (size > maxUploadBytes) {
                return Optional.empty();
            }
            return uploadBytes(bytes, filename, mimeType, uploadedBy);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Optional<FileUploadResult> uploadStream(InputStream data, String filename, String mimeType,
                                                   UserId uploadedBy) throws IOException {
        var baos = new ByteArrayOutputStream();
        var buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = data.read(buf)) >= 0) {
            total += n;
            if (total > maxUploadBytes) {
                return Optional.empty();
            }
            baos.write(buf, 0, n);
        }
        return uploadBytes(baos.toByteArray(), filename, mimeType, uploadedBy);
    }

    private Optional<FileUploadResult> uploadBytes(byte[] bytes, String filename, String mimeType,
                                                   UserId uploadedBy) {
        var fileId = FileId.of(uuidGenerator.randomUuid());
        var safeName = filename != null ? filename : "file";
        var contentType = mimeType != null ? mimeType : "application/octet-stream";
        if (fileDedupEnabled) {
            var hash = sha256Hex(bytes);
            var storageKey = DEDUP_PREFIX + hash;
            var existing = fileMetadataPort.findBlobByContentHash(hash);
            if (existing.isPresent()) {
                if (!fileMetadataPort.incrementBlobRefCount(hash)) {
                    return Optional.empty();
                }
                FileDedupMetrics.savedBytes(bytes.length);
                return fileMetadataPort.insertWithStorage(
                        fileId, safeName, contentType, bytes.length, uploadedBy, hash, storageKey)
                    .map(stored -> new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download"));
            }
            try {
                objectStoragePort.put(storageKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
            } catch (Exception e) {
                return Optional.empty();
            }
            if (!fileMetadataPort.insertBlob(hash, storageKey, bytes.length)) {
                try {
                    objectStoragePort.delete(storageKey);
                } catch (Exception ignored) {
                    // best effort rollback
                }
                return Optional.empty();
            }
            return fileMetadataPort.insertWithStorage(
                    fileId, safeName, contentType, bytes.length, uploadedBy, hash, storageKey)
                .map(stored -> new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download"));
        }
        var objectName = fileId.value().toString() + "/" + safeName;
        try {
            objectStoragePort.put(objectName, new ByteArrayInputStream(bytes), bytes.length, contentType);
        } catch (Exception e) {
            return Optional.empty();
        }
        return fileMetadataPort.insert(fileId, safeName, contentType, bytes.length, uploadedBy)
            .map(stored -> new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download"));
    }

    public InputStream download(FileId fileId) {
        var meta = fileMetadataPort.findById(fileId);
        if (meta.isEmpty()) {
            return null;
        }
        try {
            return objectStoragePort.get(meta.get().resolveObjectKey());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean delete(FileId fileId) {
        var meta = fileMetadataPort.findById(fileId);
        if (meta.isEmpty()) {
            return false;
        }
        var stored = meta.get();
        if (!fileMetadataPort.delete(fileId)) {
            return false;
        }
        if (stored.contentHash() != null && !stored.contentHash().isBlank()) {
            var remaining = fileMetadataPort.decrementBlobRefCount(stored.contentHash());
            if (remaining.isPresent() && remaining.get() <= 0) {
                try {
                    objectStoragePort.delete(stored.resolveObjectKey());
                } catch (Exception e) {
                    // metadata already removed
                }
            }
            return true;
        }
        try {
            objectStoragePort.delete(stored.resolveObjectKey());
        } catch (Exception e) {
            // metadata delete succeeded
        }
        return true;
    }

    public boolean mayView(StoredFile file, UserId viewerId) {
        if (file.uploadedBy().equals(viewerId)) {
            return true;
        }
        return legacyMessageRepository.viewerMayAccessFileViaSharedNonE2eeMessage(
            file.id().value(), viewerId.value());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record FileUploadResult(StoredFile file, String downloadUrl) {}
}
