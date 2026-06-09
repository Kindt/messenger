package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

/** File metadata aggregate identifier (hexagonal domain type). */
public record FileId(UUID value) {
    public FileId {
        if (value == null) {
            throw new IllegalArgumentException("file id required");
        }
    }

    public static FileId of(UUID value) {
        return new FileId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
