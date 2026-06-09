package com.avandocmsg.messenger.core.application;

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
import java.util.Optional;

/** Hexagonal application service for file metadata and storage (Phase 2d / US2). */
public final class FileApplicationService {
    private final FileMetadataPort fileMetadataPort;
    private final MessageRepository legacyMessageRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UuidGenerator uuidGenerator;
    private final long maxUploadBytes;

    public FileApplicationService(FileMetadataPort fileMetadataPort,
                                  MessageRepository legacyMessageRepository,
                                  ObjectStoragePort objectStoragePort,
                                  UuidGenerator uuidGenerator,
                                  long maxUploadBytes) {
        this.fileMetadataPort = fileMetadataPort;
        this.legacyMessageRepository = legacyMessageRepository;
        this.objectStoragePort = objectStoragePort;
        this.uuidGenerator = uuidGenerator;
        this.maxUploadBytes = maxUploadBytes;
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
        var fileId = FileId.of(uuidGenerator.randomUuid());
        var safeName = filename != null ? filename : "file";
        var objectName = fileId.value().toString() + "/" + safeName;
        var contentType = mimeType != null ? mimeType : "application/octet-stream";
        try {
            objectStoragePort.put(objectName, data, size, contentType);
        } catch (Exception e) {
            return Optional.empty();
        }
        return fileMetadataPort.insert(fileId, filename, contentType, size, uploadedBy)
            .map(stored -> new FileUploadResult(stored, "/api/v1/files/" + fileId.value() + "/download"));
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
        var bytes = baos.toByteArray();
        return upload(new ByteArrayInputStream(bytes), filename, mimeType, bytes.length, uploadedBy);
    }

    public InputStream download(FileId fileId) {
        var meta = fileMetadataPort.findById(fileId);
        if (meta.isEmpty()) {
            return null;
        }
        var objectName = objectName(fileId, meta.get().filename());
        try {
            return objectStoragePort.get(objectName);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean delete(FileId fileId) {
        var meta = fileMetadataPort.findById(fileId);
        if (meta.isEmpty()) {
            return false;
        }
        try {
            objectStoragePort.delete(objectName(fileId, meta.get().filename()));
        } catch (Exception e) {
            // metadata delete still attempted
        }
        return fileMetadataPort.delete(fileId);
    }

    public boolean mayView(StoredFile file, UserId viewerId) {
        if (file.uploadedBy().equals(viewerId)) {
            return true;
        }
        return legacyMessageRepository.viewerMayAccessFileViaSharedNonE2eeMessage(
            file.id().value(), viewerId.value());
    }

    private static String objectName(FileId fileId, String filename) {
        return fileId.value().toString() + "/" + (filename != null ? filename : "file");
    }

    public record FileUploadResult(StoredFile file, String downloadUrl) {}
}
