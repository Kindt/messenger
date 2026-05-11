package com.avandocmsg.messenger.common.retention;

import com.avandocmsg.messenger.common.util.Sha256Hex;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Integrity digest for MinIO JSON envelopes (retention hot-body snapshots and {@code DeepArchiverWorker}
 * {@code messages/{id}.json}): {@link ArchiveSnapshotFormat#JSON_SNAPSHOT_SHA256} is the SHA-256 (hex) of UTF-8 JSON
 * bytes of the root object <em>without</em> that property, then the property is appended for the bytes actually
 * written to object storage.
 */
public final class ArchiveSnapshotEnvelopeDigest {

    private ArchiveSnapshotEnvelopeDigest() {
    }

    /**
     * Mutates {@code envelope} by appending {@link ArchiveSnapshotFormat#JSON_SNAPSHOT_SHA256}.
     *
     * @return same digest string written to the node
     */
    public static String computeAndAttach(ObjectMapper mapper, ObjectNode envelope) throws IOException {
        byte[] envelopeUtf8 = mapper.writeValueAsBytes(envelope);
        String hex = Sha256Hex.of(envelopeUtf8);
        envelope.put(ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256, hex);
        return hex;
    }
}
