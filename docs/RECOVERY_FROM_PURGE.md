# Recovery from HOT_ROW_PURGED

When a message row was purged from hot PostgreSQL but archive/deep/MinIO snapshots exist:

1. Locate metadata in Archive DB and deep-archive JSON (`messages/{id}.json`).
2. Re-insert into `messages` with `content = NULL` if body only exists in MinIO retention snapshot.
3. Restore FK integrity: ensure `chat_id`, `sender_id` still valid.
4. Re-index Solr: publish `MessageWorkerEvent.forIndexDelete` inverse — upsert via `index_op=update` from restored row.

Validate on a staging copy before production recovery.
