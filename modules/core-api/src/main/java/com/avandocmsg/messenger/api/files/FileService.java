package com.avandocmsg.messenger.api.files;

import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.api.files.dto.FileUploadResponse;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.FileDomainMapper;
import com.avandocmsg.messenger.core.port.FileMessageRef;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/** Legacy façade delegating file I/O to {@link FileApplicationService}. */
public class FileService {
    private final FileApplicationService fileApplicationService;
    private final MessageQueryPort messageQueryPort;

    public FileService(FileApplicationService fileApplicationService, MessageQueryPort messageQueryPort) {
        this.fileApplicationService = fileApplicationService;
        this.messageQueryPort = messageQueryPort;
    }

    public long maxUploadBytes() {
        return fileApplicationService.maxUploadBytes();
    }

    public FileUploadResponse upload(InputStream data, String filename, String mimeType, long size, UUID uploadedBy) {
        return fileApplicationService.upload(data, filename, mimeType, size, UserId.of(uploadedBy))
            .map(result -> new FileUploadResponse(
                result.file().id().value().toString(),
                result.file().filename(),
                result.file().mimeType(),
                result.file().size(),
                result.downloadUrl()))
            .orElse(null);
    }

    public FileUploadResponse uploadStream(InputStream data, String filename, String mimeType, UUID uploadedBy)
        throws IOException {
        return fileApplicationService.uploadStream(data, filename, mimeType, UserId.of(uploadedBy))
            .map(result -> new FileUploadResponse(
                result.file().id().value().toString(),
                result.file().filename(),
                result.file().mimeType(),
                result.file().size(),
                result.downloadUrl()))
            .orElse(null);
    }

    public InputStream download(String fileId) {
        return fileApplicationService.download(FileId.of(UUID.fromString(fileId)));
    }

    public FileInfoResponse getInfo(String fileId) {
        return fileApplicationService.findById(FileId.of(UUID.fromString(fileId)))
            .map(FileDomainMapper::toResponse)
            .orElse(null);
    }

    public boolean mayViewFile(FileInfoResponse info, UUID fileId, UUID viewerId) {
        if (info.uploadedBy().equals(viewerId.toString())) {
            return true;
        }
        return messageQueryPort.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);
    }

    public Optional<FileMessageRef> findMessageRefForViewer(UUID fileId, UUID viewerId) {
        var info = getInfo(fileId.toString());
        if (info == null || !mayViewFile(info, fileId, viewerId)) {
            return Optional.empty();
        }
        return messageQueryPort.findLatestMessageRefForViewer(fileId, viewerId);
    }

    public boolean delete(String fileId) {
        return fileApplicationService.delete(FileId.of(UUID.fromString(fileId)));
    }
}
