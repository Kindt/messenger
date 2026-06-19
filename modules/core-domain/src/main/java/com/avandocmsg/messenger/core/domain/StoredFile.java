package com.avandocmsg.messenger.core.domain;

/** Minimal file metadata aggregate for read path (Phase 2d). */
public record StoredFile(
    FileId id,
    String filename,
    String mimeType,
    long size,
    UserId uploadedBy,
    String contentHash,
    String storageKey
) {
    public StoredFile(FileId id, String filename, String mimeType, long size, UserId uploadedBy) {
        this(id, filename, mimeType, size, uploadedBy, null, null);
    }

    public String resolveObjectKey() {
        if (storageKey != null && !storageKey.isBlank()) {
            return storageKey;
        }
        return id.value().toString() + "/" + (filename != null ? filename : "file");
    }
}
