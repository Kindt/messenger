package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.core.domain.StoredFile;

public final class FileDomainMapper {
    private FileDomainMapper() {
    }

    public static FileInfoResponse toResponse(StoredFile file) {
        return new FileInfoResponse(
            file.id().value().toString(),
            file.filename(),
            file.mimeType(),
            file.size(),
            file.uploadedBy().value().toString(),
            null);
    }
}
