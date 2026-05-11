-- Поддержка SELECT кандидатов hot-body: `RetentionHotBodyJanitor.hotBodyCandidateSelectSql`
-- (статические предикаты на `messages` + сортировка по возрасту; политика / LATERAL — вне индекса).
-- Не дублирует `V010__hot_path_indexes.sql` (`idx_messages_chat_created_not_deleted`: ведущий ключ `chat_id`).

CREATE INDEX IF NOT EXISTS idx_messages_retention_hot_body_candidates
    ON messages (created_at ASC, chat_id)
    WHERE deleted = false
      AND content IS NOT NULL
      AND trim(content) <> '';
