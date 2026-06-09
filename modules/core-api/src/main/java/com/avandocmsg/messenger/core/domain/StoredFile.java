package com.avandocmsg.messenger.core.domain;

/** Minimal file metadata aggregate for read path (Phase 2d). */
public record StoredFile(
    FileId id,
    String filename,
    String mimeType,
    long size,
    UserId uploadedBy
) {}
