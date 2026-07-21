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
    private static final String CATEGORY_CHAT_DATA = "chat_data";
    private static final String CATEGORY_ATTACHMENTS = "attachments";
    private static final String CATEGORY_PERSONAL_DATA = "personal_data";
    private static final String CATEGORY_ARCHIVE = "archive";
    private static final String CATEGORY_SEARCH_INDEX = "search_index";
    private static final String CATEGORY_RETENTION = "retention";

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

    public static ArrayNode build( // NOSONAR java:S107 -- public export API; callers pass flat flags
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
        addDisclosure(arr, "hot_messages", CATEGORY_CHAT_DATA, true,
            "Message rows from hot PostgreSQL (metadata; E2EE without plaintext; "
                + nonE2eeMessageCount + " non-E2EE message(s) in scope).");
        addDisclosure(arr, "e2ee_plaintext", CATEGORY_PERSONAL_DATA, false,
            "End-to-end encrypted message bodies are never exported in plaintext.");
        addDisclosure(arr, "e2ee_file_refs", CATEGORY_ATTACHMENTS, false,
            e2eeFileRefsNote(e2eeMessageCount));
        addDisclosure(arr, "e2ee_file_candidates", CATEGORY_ATTACHMENTS, e2eeFileCandidatesIncluded,
            e2eeFileCandidatesNote(e2eeFileCandidatesIncluded, e2eeFileCandidateCount, e2eeMessageCount));
        addDisclosure(arr, "message_versions", CATEGORY_CHAT_DATA, includeVersions,
            includeVersions ? "Edit history included when enabled." : "Edit history not included in this export.");
        addDisclosure(arr, "reactions", CATEGORY_CHAT_DATA, includeReactions,
            includeReactions ? "Reactions included." : "Reactions not included.");
        addDisclosure(arr, "pinned_messages", CATEGORY_CHAT_DATA, includePins,
            includePins ? "Pinned message references included." : "Pins not included.");
        addDisclosure(arr, "file_metadata", CATEGORY_ATTACHMENTS, includeReferencedFiles,
            includeReferencedFiles ? "Referenced file metadata (not binary bodies)." : "File metadata omitted.");
        addDisclosure(arr, "file_binary", CATEGORY_ATTACHMENTS, false,
            fileBodiesRequested
                ? "See fileBodies in export root and attachments/ in zip when bundle was built."
                : "Attachment file bytes are not part of export v1 JSON unless EXPORT_REPLAY_INCLUDE_FILE_BODIES.");
        addDisclosure(arr, "deep_archive_bodies", CATEGORY_ARCHIVE,
            deepArchiveSnapshotsFound > 0,
            deepArchiveBodiesNote(deepArchiveSnapshotsRequested, deepArchiveSnapshotsFound, deepArchiveFileIdsReferenced));
        addDisclosure(arr, "retention_snapshots", CATEGORY_ARCHIVE, retentionSnapshotsFound > 0,
            retentionSnapshotsNote(retentionSnapshotsRequested, retentionSnapshotsFound));
        addDisclosure(arr, "solr_index", CATEGORY_SEARCH_INDEX, solrExported > 0,
            solrIndexNote(solrIndexRequested, solrExported));
        addDisclosure(arr, "ttl_filtered_messages", CATEGORY_RETENTION, !messageTtlFilterApplied,
            messageTtlFilterApplied
                ? "Messages past per-message TTL were excluded (same rule as live API)."
                : "Per-message TTL filter was off; expired-TTL rows may appear.");
        return arr;
    }

    private static String e2eeFileRefsNote(int e2eeMessageCount) {
        if (e2eeMessageCount > 0) {
            return "File UUIDs embedded in " + e2eeMessageCount + " E2EE message(s) are not extracted from ciphertext.";
        }
        return "No E2EE messages in this export; file refs come from plaintext content and chat avatar only.";
    }

    private static String e2eeFileCandidatesNote(
        boolean e2eeFileCandidatesIncluded,
        int e2eeFileCandidateCount,
        int e2eeMessageCount
    ) {
        if (e2eeFileCandidatesIncluded) {
            return e2eeFileCandidateCount
                + " heuristic file_metadata row(s) uploaded by chat members (not from E2EE bodies).";
        }
        if (e2eeMessageCount > 0) {
            return "Optional heuristic list omitted; set EXPORT_REPLAY_INCLUDE_E2EE_FILE_CANDIDATES=true to include.";
        }
        return "Not applicable — no E2EE messages in this export.";
    }

    private static String deepArchiveBodiesNote(
        boolean deepArchiveSnapshotsRequested,
        int deepArchiveSnapshotsFound,
        boolean deepArchiveFileIdsReferenced
    ) {
        if (!deepArchiveSnapshotsRequested) {
            return deepArchiveFileIdsReferenced
                ? "Deep-archive snapshots not requested; export still references deep-archive file IDs."
                : "Deep-archive snapshots not requested for this export.";
        }
        if (deepArchiveSnapshotsFound > 0) {
            return "Deep-archive MinIO snapshots merged for " + deepArchiveSnapshotsFound + " message(s).";
        }
        return "Deep-archive requested but no snapshots found in object storage.";
    }

    private static String retentionSnapshotsNote(boolean retentionSnapshotsRequested, int retentionSnapshotsFound) {
        if (!retentionSnapshotsRequested) {
            return "Retention MinIO snapshots not requested.";
        }
        if (retentionSnapshotsFound > 0) {
            return "Retention hot-body MinIO snapshots included for " + retentionSnapshotsFound + " message(s).";
        }
        return "Retention snapshots requested but none found.";
    }

    private static String solrIndexNote(boolean solrIndexRequested, int solrExported) {
        if (!solrIndexRequested) {
            return "Solr index not included.";
        }
        if (solrExported > 0) {
            return "Solr index documents exported (" + solrExported + ").";
        }
        return "Solr dump requested but no documents exported.";
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
