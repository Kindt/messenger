package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.metrics.FileDedupMetrics;
import com.avandocmsg.messenger.api.metrics.FileUploadMetrics;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** Hexagonal application service for file metadata and storage (Phase 2d / US2). */
public final class FileApplicationService {
    private static final String DEDUP_PREFIX = "objects/sha256/";

    private final FileMetadataPort fileMetadataPort;
    private final MessageRepository legacyMessageRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UuidGenerator uuidGenerator;
    private final long maxUploadBytes;
    private final boolean fileDedupEnabled;
    private final Semaphore uploadConcurrency;

    public FileApplicationService(FileMetadataPort fileMetadataPort,
                                  MessageRepository legacyMessageRepository,
                                  ObjectStoragePort objectStoragePort,
                                  UuidGenerator uuidGenerator,
                                  long maxUploadBytes,
                                  boolean fileDedupEnabled) {
        this(fileMetadataPort, legacyMessageRepository, objectStoragePort, uuidGenerator,
            maxUploadBytes, fileDedupEnabled, 20);
    }

    public FileApplicationService(FileMetadataPort fileMetadataPort,
                                  MessageRepository legacyMessageRepository,
                                  ObjectStoragePort objectStoragePort,
                                  UuidGenerator uuidGenerator,
                                  long maxUploadBytes,
                                  boolean fileDedupEnabled,
                                  int maxConcurrentUploads) {
        this.fileMetadataPort = fileMetadataPort;
        this.legacyMessageRepository = legacyMessageRepository;
        this.objectStoragePort = objectStoragePort;
        this.uuidGenerator = uuidGenerator;
        this.maxUploadBytes = maxUploadBytes;
        this.fileDedupEnabled = fileDedupEnabled;
        this.uploadConcurrency = new Semaphore(Math.max(1, maxConcurrentUploads), true);
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
        return uploadWithSpool(data, filename, mimeType, size, uploadedBy);
    }

    public Optional<FileUploadResult> uploadStream(InputStream data, String filename, String mimeType,
                                                   UserId uploadedBy) throws IOException {
        return uploadWithSpool(data, filename, mimeType, -1, uploadedBy);
    }

    private Optional<FileUploadResult> uploadWithSpool(InputStream data, String filename, String mimeType,
                                                       long declaredSize, UserId uploadedBy) {
        if (!uploadConcurrency.tryAcquire()) {
            return Optional.empty();
        }
        try {
            var spoolOpt = UploadSpool.from(data, declaredSize, maxUploadBytes);
            if (spoolOpt.isEmpty()) {
                return Optional.empty();
            }
            try (var spool = spoolOpt.get()) {
                if (spool.size() == 0) {
                    return Optional.empty();
                }
                return persistSpooled(spool, filename, mimeType, uploadedBy);
            }
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            uploadConcurrency.release();
        }
    }

    private Optional<FileUploadResult> persistSpooled(UploadSpool spool, String filename, String mimeType,
                                                        UserId uploadedBy) {
        var fileId = FileId.of(uuidGenerator.randomUuid());
        var safeName = filename != null ? filename : "file";
        var contentType = mimeType != null ? mimeType : "application/octet-stream";
        var size = spool.size();
        if (fileDedupEnabled) {
            var hash = spool.sha256Hex();
            var storageKey = DEDUP_PREFIX + hash;
            var existing = fileMetadataPort.findBlobByContentHash(hash);
            if (existing.isPresent()) {
                if (!fileMetadataPort.incrementBlobRefCount(hash)) {
                    return Optional.empty();
                }
                FileDedupMetrics.savedBytes(size);
                return fileMetadataPort.insertWithStorage(
                        fileId, safeName, contentType, size, uploadedBy, hash, storageKey)
                    .map(stored -> {
                        FileUploadMetrics.uploadedBytes(size);
                        return new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download");
                    });
            }
            try (var in = spool.open()) {
                objectStoragePort.put(storageKey, in, size, contentType);
            } catch (Exception e) {
                return Optional.empty();
            }
            if (!fileMetadataPort.insertBlob(hash, storageKey, size)) {
                try {
                    objectStoragePort.delete(storageKey);
                } catch (Exception ignored) {
                    // best effort rollback
                }
                return Optional.empty();
            }
            return fileMetadataPort.insertWithStorage(
                    fileId, safeName, contentType, size, uploadedBy, hash, storageKey)
                .map(stored -> {
                    FileUploadMetrics.uploadedBytes(size);
                    return new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download");
                });
        }
        var objectName = fileId.value().toString() + "/" + safeName;
        try (var in = spool.open()) {
            objectStoragePort.put(objectName, in, size, contentType);
        } catch (Exception e) {
            return Optional.empty();
        }
            return fileMetadataPort.insert(fileId, safeName, contentType, size, uploadedBy)
            .map(stored -> {
                FileUploadMetrics.uploadedBytes(size);
                return new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download");
            });
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

    public record FileUploadResult(StoredFile file, String downloadUrl) {}
}
