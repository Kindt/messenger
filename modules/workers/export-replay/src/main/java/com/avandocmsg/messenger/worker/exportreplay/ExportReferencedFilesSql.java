package com.avandocmsg.messenger.worker.exportreplay;

/**
 * SQL fragments for referenced file metadata in export replay (split from {@link ExportReplayWorker}).
 */
final class ExportReferencedFilesSql {

    static final String REFERENCED_FILES = """
        SELECT id, filename, mime_type, size, uploaded_by, created_at
        FROM file_metadata
        WHERE id = ANY(?::uuid[])
        ORDER BY created_at ASC
        """;

    /** Heuristic: files uploaded by chat members, excluding IDs already in {@code referencedFiles}. */
    static final String E2EE_FILE_CANDIDATES = """
        SELECT fm.id, fm.filename, fm.mime_type, fm.size, fm.uploaded_by, fm.created_at
        FROM file_metadata fm
        WHERE fm.uploaded_by IN (
            SELECT cm.user_id FROM chat_members cm WHERE cm.chat_id = ?::uuid
        )
        AND NOT (fm.id = ANY(?::uuid[]))
        ORDER BY fm.created_at ASC
        LIMIT ?
        """;

    private ExportReferencedFilesSql() {
    }
}
