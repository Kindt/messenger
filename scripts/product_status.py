"""Product implementation status — single source for product_presentation.html."""

from html import escape

PRODUCT_VERSION = "2.5.1"
PRODUCT_DATE = "16 июня 2026"
PLAYWRIGHT_PASSED = 30
PLAYWRIGHT_DATE = "2026-06-15"

# (id, label, status, note) — status: done | partial | planned | out
FEATURES: tuple[tuple[str, str, str, str], ...] = (
    ("web_client", "Веб-клиент (браузер, PWA)", "done", "основные пользовательские сценарии"),
    ("server", "Сервер и фоновые службы", "done", ""),
    ("admin", "Админ-консоль (/admin/)", "done", "отдельная веб-консоль администратора"),
    ("auth", "Вход Keycloak (локальные пользователи)", "done", ""),
    ("chats", "Чаты, сообщения, файлы, export", "done", ""),
    ("search_full", "Поиск Solr (Standard/full-server)", "done", ""),
    ("search_pilot", "Поиск SQL (Pilot)", "done", "без Solr"),
    ("retention", "Ретенция, deep-archive, legal hold", "done", "сжатие архива zstd"),
    ("e2ee", "E2EE / hybrid MLS", "partial", "инженерная приёмка ✓; prod после sign-off"),
    ("calls", "Видеозвонки WebRTC (mesh)", "partial", "P2P из чата; TURN — ops"),
    ("push", "Web Push / PWA", "partial", "UI и worker; prod VAPID — ops"),
    ("tls", "Prod HTTPS / TLS", "partial", "развёртывание на stage/prod — ops"),
    ("gdpr_export", "Export GDPR completeness", "partial", "export JSON/ZIP ✓; legal checklist — заказчик"),
    ("fr_opt", "Профили Pilot / Standard", "done", "lean Pilot + масштабируемый Standard"),
    ("fr_opt_dedup", "Дедупликация файлов", "done", "одинаковые вложения хранятся один раз"),
    ("fr_opt_shard", "Sharding PostgreSQL", "planned", "Enterprise roadmap"),
    ("load_test", "Formal load test на stage", "partial", "лабораторный baseline ✓; soak на stage — pending"),
    ("bot_api", "Bot API (REST / long-poll)", "planned", "не публичный API"),
    ("sso", "SSO Google / LDAP / SAML", "planned", ""),
    ("live", "Live-streaming (HLS)", "planned", ""),
    ("mobile", "Мобильные iOS/Android", "out", "вне текущей поставки"),
    ("desktop", "Desktop-клиент", "planned", ""),
)

STATUS_TAG = {
    "done": ("tag-done", "Реализовано"),
    "partial": ("tag-partial", "Частично"),
    "planned": ("tag-planned", "Запланировано"),
    "out": ("tag-out", "Вне текущей поставки"),
}


def tag_html(status: str) -> str:
    cls, label = STATUS_TAG.get(status, ("tag-out", status))
    return f'<span class="tag {cls}">{label}</span>'


def render_product_snapshot_html() -> str:
    return f"""
<div class="note">
  <div class="req">Состояние продукта на {PRODUCT_DATE}</div>
  <div class="comment">
    <b>Проверено автоматически:</b> Playwright <b>{PLAYWRIGHT_PASSED}/{PLAYWRIGHT_PASSED}</b> на тестовом стенде ({PLAYWRIGHT_DATE}),
    основные пользовательские сценарии и REST API покрыты приёмочными тестами.<br/>
    <b>До промышленного запуска (ops):</b> TLS на stage/prod, formal sign-off E2EE, Web Push на prod,
    TURN для звонков за NAT, нагрузочный тест на stage, юридическая политика GDPR export.<br/>
    <b>Не в текущей поставке:</b> мобильные приложения, Live HLS, публичный Bot API, sharding PG на Enterprise.
  </div>
</div>
"""
