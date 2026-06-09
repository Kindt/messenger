package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

/** Port for file metadata reads and writes (Phase 2d / US2). */
public interface FileMetadataPort {
    Optional<StoredFile> findById(FileId id);

    Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size, UserId uploadedBy);

    boolean delete(FileId id);
}
