#!/usr/bin/env python3
"""One-shot script to apply worker log i18n — run from repo root."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

COMMON_EN = """\
# Shared API messages (common module)
error.internal=Internal server error
error.insufficient_role=Insufficient role
worker.health.ok=healthy
worker.health.not_ready=not ready
worker.common.locale=Worker i18n locale={0}
worker.common.connected_nats=Connected to NATS at {0}
worker.common.connected_nats_jetstream=Connected to NATS at {0} (JetStream mode: {1})
worker.common.subscribed=Subscribed to {0} (queue: {1})
worker.common.subscribed_for=Subscribed to {0} (queue: {1}) for {2}
worker.common.subscribed_extra=Subscribed to {0} (queue: {1}) {2}
worker.common.subscribed_jetstream=JetStream subscribed to {0} queue {1} (consumer {2}). Waiting...
worker.common.subscribed_waiting=Subscribed to {0} (queue: {1}). Waiting for messages...
worker.common.fatal_error=Fatal error
worker.common.nats_close_error=Error closing NATS connection
worker.common.nats_connect_failed=Failed to connect NATS
worker.common.interrupted=Interrupted
worker.common.nak_failed=nak() failed
worker.common.process_message_failed=Failed to process message
worker.common.db_url_required=Set DB_JDBC_URL or {0} for {1}
worker.common.schema_inspect_failed=Could not inspect schema for {0}
worker.common.members_load_failed=Failed to get chat members for {0}
worker.common.members_list_failed=Failed to list chat members for {0}
worker.common.membership_check_failed=Failed to check chat membership for {0} in {1}
worker.common.publish_failed=Failed to publish {0} for messageId={1}
worker.common.publish_failed_simple=Failed to publish {0}
"""

COMMON_RU = """\
# Общие сообщения API (модуль common)
error.internal=Внутренняя ошибка сервера
error.insufficient_role=Недостаточно прав
worker.health.ok=ok
worker.health.not_ready=не готов
worker.common.locale=Локаль i18n воркера={0}
worker.common.connected_nats=Подключено к NATS: {0}
worker.common.connected_nats_jetstream=Подключено к NATS: {0} (режим JetStream: {1})
worker.common.subscribed=Подписка на {0} (очередь: {1})
worker.common.subscribed_for=Подписка на {0} (очередь: {1}) для {2}
worker.common.subscribed_extra=Подписка на {0} (очередь: {1}) {2}
worker.common.subscribed_jetstream=JetStream: подписка на {0}, очередь {1} (consumer {2}). Ожидание...
worker.common.subscribed_waiting=Подписка на {0} (очередь: {1}). Ожидание сообщений...
worker.common.fatal_error=Критическая ошибка
worker.common.nats_close_error=Ошибка закрытия соединения NATS
worker.common.nats_connect_failed=Не удалось подключиться к NATS
worker.common.interrupted=Прервано
worker.common.nak_failed=nak() не выполнен
worker.common.process_message_failed=Не удалось обработать сообщение
worker.common.db_url_required=Укажите DB_JDBC_URL или {0} для {1}
worker.common.schema_inspect_failed=Не удалось проверить схему для {0}
worker.common.members_load_failed=Не удалось получить участников чата {0}
worker.common.members_list_failed=Не удалось получить список участников чата {0}
worker.common.membership_check_failed=Не удалось проверить членство {0} в чате {1}
worker.common.publish_failed=Не удалось опубликовать {0} для messageId={1}
worker.common.publish_failed_simple=Не удалось опубликовать {0}
"""

MODULE_BUNDLES: dict[str, dict[str, str]] = {
    "archiver": {
        "worker.module": "archiver",
        "worker.archiver.archive_db_disabled": "Archive DB disabled (ARCHIVE_JDBC_URL not set); metadata is not written; deep-archive handoff is still published per event.",
        "worker.archiver.handle_failed": "Failed to handle archiver message",
        "worker.archiver.delete_failed": "Archive delete failed for messageId={0}",
        "worker.archiver.upsert_failed": "Archive DB upsert failed for messageId={0}",
        "worker.archiver.deep_handoff_published": "Published deep-archive handoff messageId={0}",
    },
    "bot_delivery": {
        "worker.module": "bot-delivery",
        "worker.bot_delivery.no_webhook_targets": "No bot webhook targets for chatId={0} messageId={1}",
        "worker.bot_delivery.handle_failed": "Failed to handle bot-delivery message",
        "worker.bot_delivery.duplicate_delivery": "Duplicate bot delivery (at-least-once) messageId={0} webhook={1}",
        "worker.bot_delivery.webhook_status": "Bot webhook status={0} messageId={1} url={2}",
        "worker.bot_delivery.db_url_required": "PUSH_DB_JDBC_URL",
        "worker.bot_delivery.worker_name": "BotDeliveryWorker",
    },
    "deep_archiver": {
        "worker.module": "deep-archiver",
        "worker.deep_archiver.minio_enabled": "MinIO deep-archive writes enabled bucket={0}",
        "worker.deep_archiver.minio_disabled": "MinIO not configured (set MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY); logging receipt INFO only",
        "worker.deep_archiver.bucket_created": "Created MinIO bucket {0}",
        "worker.deep_archiver.received": "Deep-archiver received messageId={0} chatId={1}",
        "worker.deep_archiver.skipped_file_ref": "Skipped deep-archive for message {0}: content is file reference",
        "worker.deep_archiver.stored_object": "Stored deep-archive object {0}",
        "worker.deep_archiver.handle_failed": "Failed to handle deep-archiver message",
        "worker.deep_archiver.wrote_chunks": "Wrote {0} chunks for message {1} (total {2} bytes)",
    },
    "indexer": {
        "worker.module": "indexer",
        "worker.indexer.skip_empty_id": "Indexer skip: empty message_id",
        "worker.indexer.handle_failed": "Failed to handle indexer message",
        "worker.indexer.deleted_solr": "Deleted from Solr id={0}",
        "worker.indexer.cleared_content": "Cleared content_txt for Solr id={0}",
        "worker.indexer.indexed_solr": "Indexed Solr id={0}",
        "worker.indexer.solr_cloud_mode": "Solr Cloud mode ZK={0} collection={1}",
        "worker.indexer.solr_http_mode": "Solr HTTP mode baseUrl={0}",
        "worker.indexer.solr_disabled": "Solr indexing disabled (set SOLR_URL or SOLR_ZK plus SOLR_COLLECTION); msg.event.index events will not be indexed",
        "worker.indexer.solr_close_error": "Error closing Solr client",
    },
    "preview": {
        "worker.module": "preview",
        "worker.preview.handle_failed": "Failed to handle preview message",
        "worker.preview.cache_hit": "Preview cache hit messageId={0} url={1} title={2}",
        "worker.preview.link_preview": "Link preview messageId={0} url={1} title={2}",
        "worker.preview.url_rejected": "Preview URL rejected: {0}",
        "worker.preview.http_status": "Preview HTTP status {0} for {1}",
        "worker.preview.fetch_failed": "Preview fetch failed for {0}",
        "worker.preview.content_load_failed": "Failed to load message content for {0}",
    },
    "push": {
        "worker.module": "push",
        "worker.push.targets": "Push targets messageId={0} chatId={1} deviceRows={2}",
        "worker.push.token_row": "Push token row userId={0} provider={1} tokenPrefix={2}",
        "worker.push.handle_failed": "Failed to handle push message",
        "worker.push.chat_title_failed": "Chat title lookup failed: {0}",
        "worker.push.web_push_summary": "Web push messageId={0} sent={1} failed={2} expired={3}",
        "worker.push.token_cleared": "Cleared expired web push token for userId={0}",
        "worker.push.token_clear_failed": "Failed to clear push token for userId={0}: {1}",
        "worker.push.webhook_status": "PUSH_WEBHOOK_URL status={0} messageId={1}",
        "worker.push.db_url_required": "PUSH_DB_JDBC_URL",
        "worker.push.worker_name": "PushWorker",
        "worker.push.health_url": "Push worker health on http://0.0.0.0:{0}/health",
        "worker.push.web_disabled": "Web Push disabled: set PUSH_VAPID_PUBLIC_KEY and PUSH_VAPID_PRIVATE_KEY",
        "worker.push.web_enabled": "Web Push enabled (subject={0})",
        "worker.push.web_invalid_keys": "Web Push disabled: invalid VAPID keys",
        "worker.push.skip_not_subscription": "Skip web push: token is not a PushSubscription JSON",
        "worker.push.subscription_expired": "Web push subscription expired (410)",
        "worker.push.delivery_failed": "Web push delivery failed: {0}",
    },
    "message_pipeline": {
        "worker.module": "message-pipeline",
        "worker.pipeline.read_receipt_fanout_failed": "Read receipt fan-out failed",
        "worker.pipeline.read_receipt_dropped": "msg.read_receipt dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.read_receipt_debug": "msg.read_receipt from {0} in chat {1} -> {2} recipients",
        "worker.pipeline.change_fanout_failed": "Message change fan-out failed",
        "worker.pipeline.change_dropped": "msg.change dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.change_debug": "msg.change {0} in chat {1} -> {2} recipients",
        "worker.pipeline.reaction_fanout_failed": "Reaction fan-out failed",
        "worker.pipeline.reaction_dropped": "msg.reaction dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.reaction_debug": "msg.reaction {0} on {1} in chat {2} -> {3} recipients",
        "worker.pipeline.pin_fanout_failed": "Pin fan-out failed",
        "worker.pipeline.pin_dropped": "msg.pin dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.pin_debug": "msg.pin {0} on {1} in chat {2} -> {3} recipients",
        "worker.pipeline.conference_fanout_failed": "Conference fan-out failed",
        "worker.pipeline.conference_dropped": "msg.conference dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.conference_debug": "msg.conference {0} {1} in chat {2} -> {3} recipients",
        "worker.pipeline.typing_fanout_failed": "Typing fan-out failed",
        "worker.pipeline.typing_dropped": "msg.typing dropped: user {0} is not an active member of chat {1}",
        "worker.pipeline.typing_debug": "msg.typing from {0} in chat {1} -> {2} recipients",
        "worker.pipeline.rtc_fanout_failed": "RTC fan-out failed",
        "worker.pipeline.rtc_dropped": "rtc.signal dropped: sender {0} is not an active member of chat {1}",
        "worker.pipeline.rtc_debug": "rtc.signal from {0} in chat {1} -> {2} recipients",
        "worker.pipeline.jetstream_failed": "JetStream pipeline failed",
        "worker.pipeline.received_send": "Received msg.send: {0} in chat {1}",
        "worker.pipeline.fanned_out": "Fanned out to {0}",
        "worker.pipeline.processed": "Processed message {0} fanned out to {1} members",
        "worker.pipeline.downstream_publish_failed": "Fan-out ok but failed to publish msg.event.* for {0}",
    },
}

MODULE_BUNDLES_RU: dict[str, dict[str, str]] = {
    "archiver": {
        "worker.module": "archiver",
        "worker.archiver.archive_db_disabled": "Archive DB отключена (ARCHIVE_JDBC_URL не задан); метаданные не записываются; handoff deep-archive по-прежнему публикуется для каждого события.",
        "worker.archiver.handle_failed": "Не удалось обработать сообщение archiver",
        "worker.archiver.delete_failed": "Ошибка удаления из archive для messageId={0}",
        "worker.archiver.upsert_failed": "Ошибка upsert в Archive DB для messageId={0}",
        "worker.archiver.deep_handoff_published": "Опубликован handoff deep-archive messageId={0}",
    },
    "bot_delivery": {
        "worker.module": "bot-delivery",
        "worker.bot_delivery.no_webhook_targets": "Нет webhook-целей бота для chatId={0} messageId={1}",
        "worker.bot_delivery.handle_failed": "Не удалось обработать сообщение bot-delivery",
        "worker.bot_delivery.duplicate_delivery": "Дубликат доставки бота (at-least-once) messageId={0} webhook={1}",
        "worker.bot_delivery.webhook_status": "Webhook бота status={0} messageId={1} url={2}",
        "worker.bot_delivery.db_url_required": "BOT_DB_JDBC_URL",
        "worker.bot_delivery.worker_name": "BotDeliveryWorker",
    },
    "deep_archiver": {
        "worker.module": "deep-archiver",
        "worker.deep_archiver.minio_enabled": "Запись deep-archive в MinIO включена, bucket={0}",
        "worker.deep_archiver.minio_disabled": "MinIO не настроен (задайте MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY); только INFO-логирование получения",
        "worker.deep_archiver.bucket_created": "Создан бакет MinIO {0}",
        "worker.deep_archiver.received": "Deep-archiver получил messageId={0} chatId={1}",
        "worker.deep_archiver.skipped_file_ref": "Пропуск deep-archive для сообщения {0}: content — ссылка на файл",
        "worker.deep_archiver.stored_object": "Сохранён объект deep-archive {0}",
        "worker.deep_archiver.handle_failed": "Не удалось обработать сообщение deep-archiver",
        "worker.deep_archiver.wrote_chunks": "Записано {0} чанков для сообщения {1} (всего {2} байт)",
    },
    "indexer": {
        "worker.module": "indexer",
        "worker.indexer.skip_empty_id": "Indexer: пропуск — пустой message_id",
        "worker.indexer.handle_failed": "Не удалось обработать сообщение indexer",
        "worker.indexer.deleted_solr": "Удалено из Solr id={0}",
        "worker.indexer.cleared_content": "Очищен content_txt в Solr id={0}",
        "worker.indexer.indexed_solr": "Проиндексировано в Solr id={0}",
        "worker.indexer.solr_cloud_mode": "Режим Solr Cloud ZK={0} collection={1}",
        "worker.indexer.solr_http_mode": "Режим Solr HTTP baseUrl={0}",
        "worker.indexer.solr_disabled": "Индексация Solr отключена (задайте SOLR_URL или SOLR_ZK и SOLR_COLLECTION); события msg.event.index не индексируются",
        "worker.indexer.solr_close_error": "Ошибка закрытия клиента Solr",
    },
    "preview": {
        "worker.module": "preview",
        "worker.preview.handle_failed": "Не удалось обработать сообщение preview",
        "worker.preview.cache_hit": "Попадание в кэш preview messageId={0} url={1} title={2}",
        "worker.preview.link_preview": "Link preview messageId={0} url={1} title={2}",
        "worker.preview.url_rejected": "URL preview отклонён: {0}",
        "worker.preview.http_status": "HTTP-статус preview {0} для {1}",
        "worker.preview.fetch_failed": "Ошибка загрузки preview для {0}",
        "worker.preview.content_load_failed": "Не удалось загрузить content сообщения {0}",
    },
    "push": {
        "worker.module": "push",
        "worker.push.targets": "Цели push messageId={0} chatId={1} deviceRows={2}",
        "worker.push.token_row": "Строка push-токена userId={0} provider={1} tokenPrefix={2}",
        "worker.push.handle_failed": "Не удалось обработать push-сообщение",
        "worker.push.chat_title_failed": "Ошибка получения заголовка чата: {0}",
        "worker.push.web_push_summary": "Web push messageId={0} sent={1} failed={2} expired={3}",
        "worker.push.token_cleared": "Очищен просроченный web push token для userId={0}",
        "worker.push.token_clear_failed": "Не удалось очистить push token для userId={0}: {1}",
        "worker.push.webhook_status": "PUSH_WEBHOOK_URL status={0} messageId={1}",
        "worker.push.db_url_required": "PUSH_DB_JDBC_URL",
        "worker.push.worker_name": "PushWorker",
        "worker.push.health_url": "Health push-воркера: http://0.0.0.0:{0}/health",
        "worker.push.web_disabled": "Web Push отключён: задайте PUSH_VAPID_PUBLIC_KEY и PUSH_VAPID_PRIVATE_KEY",
        "worker.push.web_enabled": "Web Push включён (subject={0})",
        "worker.push.web_invalid_keys": "Web Push отключён: неверные VAPID-ключи",
        "worker.push.skip_not_subscription": "Пропуск web push: token не является JSON PushSubscription",
        "worker.push.subscription_expired": "Подписка web push истекла (410)",
        "worker.push.delivery_failed": "Ошибка доставки web push: {0}",
    },
    "message_pipeline": {
        "worker.module": "message-pipeline",
        "worker.pipeline.read_receipt_fanout_failed": "Ошибка fan-out read receipt",
        "worker.pipeline.read_receipt_dropped": "msg.read_receipt отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.read_receipt_debug": "msg.read_receipt от {0} в чате {1} -> {2} получателей",
        "worker.pipeline.change_fanout_failed": "Ошибка fan-out изменения сообщения",
        "worker.pipeline.change_dropped": "msg.change отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.change_debug": "msg.change {0} в чате {1} -> {2} получателей",
        "worker.pipeline.reaction_fanout_failed": "Ошибка fan-out реакций",
        "worker.pipeline.reaction_dropped": "msg.reaction отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.reaction_debug": "msg.reaction {0} на {1} в чате {2} -> {3} получателей",
        "worker.pipeline.pin_fanout_failed": "Ошибка fan-out закреплений",
        "worker.pipeline.pin_dropped": "msg.pin отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.pin_debug": "msg.pin {0} на {1} в чате {2} -> {3} получателей",
        "worker.pipeline.conference_fanout_failed": "Ошибка fan-out конференций",
        "worker.pipeline.conference_dropped": "msg.conference отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.conference_debug": "msg.conference {0} {1} в чате {2} -> {3} получателей",
        "worker.pipeline.typing_fanout_failed": "Ошибка fan-out typing",
        "worker.pipeline.typing_dropped": "msg.typing отброшен: пользователь {0} не активный участник чата {1}",
        "worker.pipeline.typing_debug": "msg.typing от {0} в чате {1} -> {2} получателей",
        "worker.pipeline.rtc_fanout_failed": "Ошибка fan-out RTC",
        "worker.pipeline.rtc_dropped": "rtc.signal отброшен: отправитель {0} не активный участник чата {1}",
        "worker.pipeline.rtc_debug": "rtc.signal от {0} в чате {1} -> {2} получателей",
        "worker.pipeline.jetstream_failed": "Ошибка pipeline JetStream",
        "worker.pipeline.received_send": "Получен msg.send: {0} в чате {1}",
        "worker.pipeline.fanned_out": "Fan-out на {0}",
        "worker.pipeline.processed": "Обработано сообщение {0}, fan-out на {1} участников",
        "worker.pipeline.downstream_publish_failed": "Fan-out OK, но не удалось опубликовать msg.event.* для {0}",
    },
}

WORKER_MODULE_PATH = {
    "archiver": "modules/workers/archiver/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_archiver_{lang}.properties",
    "bot_delivery": "modules/workers/bot-delivery/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_bot_delivery_{lang}.properties",
    "deep_archiver": "modules/workers/deep-archiver/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_deep_archiver_{lang}.properties",
    "indexer": "modules/workers/indexer/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_indexer_{lang}.properties",
    "preview": "modules/workers/preview/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_preview_{lang}.properties",
    "push": "modules/workers/push/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_push_{lang}.properties",
    "message_pipeline": "modules/workers/message-pipeline/src/main/resources/com/avandocmsg/messenger/i18n/messages_worker_message_pipeline_{lang}.properties",
}


def write_properties(path: Path, entries: dict[str, str]) -> None:
    lines = [f"{k}={v}" for k, v in entries.items()]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    (ROOT / "modules/common/src/main/resources/com/avandocmsg/messenger/i18n/messages_common_en.properties").write_text(
        COMMON_EN, encoding="utf-8"
    )
    (ROOT / "modules/common/src/main/resources/com/avandocmsg/messenger/i18n/messages_common_ru.properties").write_text(
        COMMON_RU, encoding="utf-8"
    )
    for mod, entries in MODULE_BUNDLES.items():
        write_properties(ROOT / WORKER_MODULE_PATH[mod].format(lang="en"), entries)
    for mod, entries in MODULE_BUNDLES_RU.items():
        write_properties(ROOT / WORKER_MODULE_PATH[mod].format(lang="ru"), entries)
    print("Properties written.")


if __name__ == "__main__":
    main()
