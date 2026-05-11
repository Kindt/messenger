package com.avandocmsg.messenger.worker.retention;

/**
 * Chooses how a retention hot-body JSON snapshot is materialized before MinIO upload ({@code putObject} / {@code uploadObject}).
 * Compares env threshold to the UTF-8 length of {@code messages.content} only (not the full JSON size).
 */
final class RetentionSnapshotMaterialization {

    private RetentionSnapshotMaterialization() {
    }

    /**
     * Temp-file path is used when threshold is enabled ({@code > 0}) and UTF-8 length of message content
     * is strictly greater than the threshold (equality keeps the existing in-memory {@code writeValueAsBytes} path).
     */
    static boolean shouldUseTempFile(long thresholdBytes, int contentUtf8Bytes) {
        return thresholdBytes > 0 && contentUtf8Bytes > thresholdBytes;
    }

    /**
     * UTF-8 encoded length without allocating a full {@code byte[]} (avoids extra heap when sizing large bodies).
     */
    static int utf8ByteLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int len = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp < 0x80) {
                len += 1;
            } else if (cp < 0x800) {
                len += 2;
            } else if (cp < 0x10000) {
                len += 3;
            } else {
                len += 4;
            }
            i += Character.charCount(cp);
        }
        return len;
    }
}
