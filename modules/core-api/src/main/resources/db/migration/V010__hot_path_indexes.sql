-- ТЗ п. 86: дополнительные индексы под частые запросы (идемпотентно: IF NOT EXISTS).

-- Блоки: запросы вида WHERE blocker_id = ? (фан-аут, лента, поиск).
CREATE INDEX IF NOT EXISTS idx_blocks_blocker_id ON blocks (blocker_id);

-- Организации: выборка пользователей по org_id (админка / multi-tenant).
CREATE INDEX IF NOT EXISTS idx_users_org_id ON users (org_id) WHERE org_id IS NOT NULL;

-- История сообщений без удалённых: меньше индекс, чем полный (chat_id, created_at).
CREATE INDEX IF NOT EXISTS idx_messages_chat_created_not_deleted
    ON messages (chat_id, created_at DESC)
    WHERE deleted = false;

-- Публичные ссылки: выборка по сроку истечения (фоновая очистка / проверки).
CREATE INDEX IF NOT EXISTS idx_file_public_links_expires_revoked
    ON file_public_links (expires_at)
    WHERE revoked_at IS NULL;
