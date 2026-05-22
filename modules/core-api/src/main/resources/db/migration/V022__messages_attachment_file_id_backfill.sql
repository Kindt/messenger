-- Backfill attachment_file_id for legacy file/image/video messages where content is the file UUID.
UPDATE messages m
SET attachment_file_id = sub.file_id
FROM (
    SELECT id AS message_id,
           trim(content)::uuid AS file_id
    FROM messages
    WHERE attachment_file_id IS NULL
      AND deleted = false
      AND type IN ('file', 'image', 'video')
      AND trim(content) ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
) sub
INNER JOIN file_metadata fm ON fm.id = sub.file_id
WHERE m.id = sub.message_id;
