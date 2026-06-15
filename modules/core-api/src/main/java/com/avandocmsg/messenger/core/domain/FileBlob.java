package com.avandocmsg.messenger.core.domain;

/** Shared blob row for content-hash deduplication (FR-OPT-08). */
public record FileBlob(String contentHash, String storageKey, long blobSize, int refCount) {}
