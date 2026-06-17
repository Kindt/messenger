package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** File upload throughput metrics (PS-2.4). */
public final class FileUploadMetrics {

    private static final Counter UPLOAD_BYTES = Counter.build()
        .name("file_upload_bytes_total")
        .help("Bytes accepted by file upload endpoint")
        .register();

    private FileUploadMetrics() {
    }

    public static void uploadedBytes(long bytes) {
        if (bytes > 0) {
            UPLOAD_BYTES.inc(bytes);
        }
    }
}
