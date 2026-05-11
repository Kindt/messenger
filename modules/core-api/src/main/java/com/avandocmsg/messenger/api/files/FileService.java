package com.avandocmsg.messenger.api.files;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.api.files.dto.FileUploadResponse;
import com.avandocmsg.messenger.api.repository.FileRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final AppConfig appConfig;
    private final FileProxy fileProxy;
    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final UuidGenerator uuidGenerator;

    public FileService(AppConfig appConfig, FileProxy fileProxy, FileRepository fileRepository,
                       MessageRepository messageRepository, UuidGenerator uuidGenerator) {
        this.appConfig = appConfig;
        this.fileProxy = fileProxy;
        this.fileRepository = fileRepository;
        this.messageRepository = messageRepository;
        this.uuidGenerator = uuidGenerator;
    }

    /** Лимит из {@link AppConfig#mediaMaxUploadBytes()} (совпадает с {@code GET /media/capabilities}). */
    public long maxUploadBytes() {
        return appConfig.mediaMaxUploadBytes();
    }

    public FileUploadResponse upload(InputStream data, String filename, String mimeType, long size, UUID uploadedBy) {
        if (size > maxUploadBytes()) {
            return null;
        }
        var id = uuidGenerator.randomUuid();
        var objectName = id.toString() + "/" + (filename != null ? filename : "file");
        try {
            fileProxy.upload(objectName, data, size, mimeType != null ? mimeType : "application/octet-stream");
        } catch (Exception e) {
            log.error("File proxy upload failed", e);
            return null;
        }
        var meta = fileRepository.insert(id, filename, mimeType != null ? mimeType : "application/octet-stream", size, uploadedBy);
        if (meta == null) {
            return null;
        }
        return new FileUploadResponse(meta.id(), meta.filename(), meta.mimeType(), meta.size(),
            "/api/v1/files/" + meta.id() + "/download");
    }

    /**
     * Читает поток до {@link #maxUploadBytes()} байт (для multipart без заранее известного размера).
     */
    public FileUploadResponse uploadStream(InputStream data, String filename, String mimeType, UUID uploadedBy) throws IOException {
        var max = maxUploadBytes();
        var baos = new ByteArrayOutputStream();
        var buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = data.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                return null;
            }
            baos.write(buf, 0, n);
        }
        var bytes = baos.toByteArray();
        return upload(new ByteArrayInputStream(bytes), filename, mimeType, bytes.length, uploadedBy);
    }

    public InputStream download(String fileId) {
        try {
            var meta = fileRepository.findById(UUID.fromString(fileId));
            if (meta.isEmpty()) {
                return null;
            }
            var objectName = fileId + "/" + (meta.get().filename() != null ? meta.get().filename() : "file");
            return fileProxy.download(objectName);
        } catch (Exception e) {
            log.warn("Failed to download file {}", fileId, e);
            return null;
        }
    }

    public FileInfoResponse getInfo(String fileId) {
        return fileRepository.findById(UUID.fromString(fileId)).orElse(null);
    }

    /** Owner, or participant who may see a shared non-E2EE message containing this file id in {@code content}. */
    public boolean mayViewFile(FileInfoResponse info, UUID fileId, UUID viewerId) {
        if (info.uploadedBy().equals(viewerId.toString())) {
            return true;
        }
        return messageRepository.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);
    }

    public boolean delete(String fileId) {
        try {
            var meta = fileRepository.findById(UUID.fromString(fileId));
            if (meta.isEmpty()) {
                return false;
            }
            var objectName = fileId + "/" + meta.get().filename();
            fileProxy.delete(objectName);
        } catch (Exception e) {
            log.warn("Failed to delete file {} from proxy", fileId, e);
        }
        return fileRepository.delete(UUID.fromString(fileId));
    }
}
