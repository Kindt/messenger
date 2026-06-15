package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.FileBlob;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

/** Port for file metadata reads and writes (Phase 2d / US2). */
public interface FileMetadataPort {
    Optional<StoredFile> findById(FileId id);

    Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size, UserId uploadedBy);

    boolean delete(FileId id);

    Optional<FileBlob> findBlobByContentHash(String contentHash);

    boolean insertBlob(String contentHash, String storageKey, long blobSize);

    boolean incrementBlobRefCount(String contentHash);

    /** @return new ref_count after decrement, or empty if blob missing */
    Optional<Integer> decrementBlobRefCount(String contentHash);

    Optional<StoredFile> insertWithStorage(FileId id, String filename, String mimeType, long size,
                                           UserId uploadedBy, String contentHash, String storageKey);
}
