package com.avandocmsg.messenger.common.export;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * GDPR-style disclosure checklist embedded in export JSON ({@code exportCompleteness.gdprDisclosures})
 * and exposed to the admin UI as a reference template.
 */
public final class ExportGdprDisclosures {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ExportGdprDisclosures() {}

    /**
     * Reference rows for operators (minimal export flags; notes describe typical defaults).
     */
    public static ArrayNode referenceTemplate() {
        return build(
            true,
            false,
            false,
            false,
            true,
            false,
            0,
            false,
            false,
            0,
            false,
            0,
            0,
            0,
            false,
            false,
            0);
    }

    public static ArrayNode build(
        boolean messageTtlFilterApplied,
        boolean includeVersions,
        boolean includeReactions,
        boolean includePins,
        boolean includeReferencedFiles,
        boolean deepArchiveSnapshotsRequested,
        int deepArchiveSnapshotsFound,
        boolean deepArchiveFileIdsReferenced,
        boolean retentionSnapshotsRequested,
        int retentionSnapshotsFound,
        boolean solrIndexRequested,
        int solrExported,
        int e2eeMessageCount,
        int nonE2eeMessageCount,
        boolean fileBodiesRequested,
        boolean e2eeFileCandidatesIncluded,
        int e2eeFileCandidateCount
    ) {
        var arr = MAPPER.createArrayNode();
        addDisclosure(arr, "hot_messages", "chat_data", true,
            "Message rows from hot PostgreSQL (metadata; E2EE without plaintext).");
        addDisclosure(arr, "e2ee_plaintext", "personal_data", false,
            "End-to-end encrypted message bodies are never exported in plaintext.");
        addDisclosure(arr, "e2ee_file_refs", "attachments", false,
            e2eeMessageCount > 0
                ? "File UUIDs embedded in " + e2eeMessageCount + " E2EE message(s) are not extracted from ciphertext."
                : "No E2EE messages in this export; file refs come from plaintext content and chat avatar only.");
        addDisclosure(arr, "e2ee_file_candidates", "attachments", e2eeFileCandidatesIncluded,
            e2eeFileCandidatesIncluded
                ? e2eeFileCandidateCount + " heuristic file_metadata row(s) uploaded by chat members (not from E2EE bodies)."
                : (e2eeMessageCount > 0
                ? "Optional heuristic list omitted; set EXPORT_REPLAY_INCLUDE_E2EE_FILE_CANDIDATES=true to include."
                : "Not applicable вЂ” no E2EE messages in this export."));
        addDisclosure(arr, "message_versions", "chat_data", includeVersions,
            includeVersions ? "Edit history included when enabled." : "Edit history not included in this export.");
        addDisclosure(arr, "reactions", "chat_data", includeReactions,
            includeReactions ? "Reactions included." : "Reactions not included.");
        addDisclosure(arr, "pinned_messages", "chat_data", includePins,
            includePins ? "Pinned message references included." : "Pins not included.");
        addDisclosure(arr, "file_metadata", "attachments", includeReferencedFiles,
            includeReferencedFiles ? "Referenced file metadata (not binary bodies)." : "File metadata omitted.");
        addDisclosure(arr, "file_binary", "attachments", false,
            fileBodiesRequested
                ? "See fileBodies in export root and attachments/ in zip when bundle was built."
                : "Attachment file bytes are not part of export v1 JSON unless EXPORT_REPLAY_INCLUDE_FILE_BODIES.");
        addDisclosure(arr, "deep_archive_bodies", "archive", deepArchiveSnapshotsFound > 0,
            deepArchiveSnapshotsRequested
                ? (deepArchiveSnapshotsFound > 0
                ? "Deep-archive MinIO snapshots merged for " + deepArchiveSnapshotsFound + " message(s)."
                : "Deep-archive requested but no snapshots found in object storage.")
                : "Deep-archive snapshots not requested for this export.");
        addDisclosure(arr, "retention_snapshots", "archive", retentionSnapshotsFound > 0,
            retentionSnapshotsRequested
                ? (retentionSnapshotsFound > 0
                ? "Retention hot-body MinIO snapshots included for " + retentionSnapshotsFound + " message(s)."
                : "Retention snapshots requested but none found.")
                : "Retention MinIO snapshots not requested.");
        addDisclosure(arr, "solr_index", "search_index", solrExported > 0,
            solrIndexRequested
                ? (solrExported > 0
                ? "Solr index documents exported (" + solrExported + ")."
                : "Solr dump requested but no documents exported.")
                : "Solr index not included.");
        addDisclosure(arr, "ttl_filtered_messages", "retention", !messageTtlFilterApplied,
            messageTtlFilterApplied
                ? "Messages past per-message TTL were excluded (same rule as live API)."
                : "Per-message TTL filter was off; expired-TTL rows may appear.");
        return arr;
    }

    private static void addDisclosure(ArrayNode arr, String id, String category, boolean included, String note) {
        var item = MAPPER.createObjectNode();
        item.put("id", id);
        item.put("category", category);
        item.put("included", included);
        item.put("note", note);
        arr.add(item);
    }
}
