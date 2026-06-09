package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;

import java.util.Optional;

/** Port for file metadata reads (Phase 2d). */
public interface FileMetadataPort {
    Optional<StoredFile> findById(FileId id);
}
