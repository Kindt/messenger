"""Product implementation status — single source for Block 0 and deck footer."""

from __future__ import annotations

from html import escape

PRODUCT_VERSION = "2.6.2"
PRODUCT_DATE = "16 июня 2026"
DECK_VERSION = "1.0.0"
BUILD_DATE = "2026-06-18"

PRODUCT_STAGE = "working_prototype"
PRODUCT_STAGE_LABEL = "Рабочий прототип"
PRODUCTION_READY = False

PLAYWRIGHT_PASSED = 34
PLAYWRIGHT_TOTAL = 34
PLAYWRIGHT_DATE = "2026-06-16"

PRODUCTION_BLOCKERS: tuple[str, ...] = (
    "Нет промышленного stage/prod стенда (планируется с сентября 2026) — formal load test и ops sign-off отложены",
    "Prod HTTPS/TLS: поставка в Ansible есть, боевые сертификаты и vault — на контуре заказчика",
    "E2EE: инженерная приёмка пройдена; требуется sign-off ИБ перед массовым включением",
    "WebRTC: mesh из чата готов; TURN в prod-контуре — настройка IT заказчика",
    "Web Push: UI/worker готовы; боевые VAPID и проверка на стенде — ops",
    "SSO/LDAP: скрипты и runbook есть; подключение live IdP/AD — ops",
    "Мобильные клиенты iOS/Android — вне текущей поставки",
    "Live-streaming (§28) — в roadmap, не реализован",
)

FEATURES: tuple[tuple[str, str, str, str], ...] = (
    ("web_client", "Веб-клиент (браузер, PWA)", "done", "основные пользовательские сценарии"),
    ("server", "Сервер и фоновые службы", "done", ""),
    ("admin", "Админ-консоль (/admin/)", "done", "отдельная веб-консоль администратора"),
    ("auth", "Вход Keycloak (локальные пользователи)", "done", ""),
    ("chats", "Чаты, сообщения, файлы, export", "done", ""),
    ("search_full", "Полнотекстовый поиск Solr", "done", "prod full"),
    ("search_sql", "Поиск SQL (fallback)", "done", "dev-min / малый контур"),
    ("retention", "Ретенция, deep-archive, legal hold", "done", "сжатие архива zstd"),
    ("security_eng", "Безопасность (§24, инженерия)", "done", "headers, rate limit, timing, WS origin; CI security-gate"),
    ("e2ee", "E2EE / hybrid MLS", "partial", "инженерная приёмка ✓; prod sign-off — ops"),
    ("calls", "Видеозвонки WebRTC (mesh)", "partial", "mesh из чата ✓; TURN prod — ops"),
    ("push", "Web Push / PWA", "partial", "UI и worker ✓; prod VAPID — ops"),
    ("tls", "Prod HTTPS / TLS", "partial", "Ansible/TLS в поставке ✓; stage host — с сентября 2026"),
    ("gdpr_export", "Export GDPR completeness", "partial", "export JSON/ZIP + guide ✓; legal strict — ops"),
    ("file_resize", "Миниатюры изображений (/resize)", "done", "embedded в core-api"),
    ("batch_replay", "Batch replay (export-replay)", "done", "JDBC + export_v1; stub отключён в prod compose"),
    ("fr_opt", "Dev-min vs prod-full compose", "done", "QEMU dev-min; prod docker-compose.full-server"),
    ("fr_opt_dedup", "Дедупликация файлов", "done", "одинаковые вложения хранятся один раз"),
    ("fr_opt_shard", "Sharding PostgreSQL", "planned", "Enterprise roadmap"),
    ("load_test", "Formal load test на stage", "partial", "QEMU/k6 scaffold ✓; stage soak — с сентября 2026"),
    ("bot_api", "Bot API (REST L2)", "done", "register, webhook, long-poll, pin/ban, rotate; prod webhook SLA — ops"),
    ("plugin_platform", "Платформа ботов-плагинов (L0–L3)", "done", "admin 3-level, bridges, polyglot sidecars; узел интеграций; smokes QEMU 6/6"),
    ("sso", "SSO Google / LDAP / SAML", "partial", "OIDC + LDAP enable scripts + runbook; live IdP/AD — ops"),
    ("live", "Live-streaming (§28)", "planned", "ADR + spec 013; реализация 12+ мес."),
    ("mobile", "Мобильные iOS/Android", "out", "вне текущей поставки"),
    ("desktop", "Desktop-клиент", "planned", ""),
)

STATUS_TAG = {
    "done": ("tag-done", "Реализовано"),
    "partial": ("tag-partial", "Частично"),
    "planned": ("tag-planned", "Запланировано"),
    "out": ("tag-out", "Вне текущей поставки"),
}


def features_by_status(status: str) -> list[tuple[str, str, str, str]]:
    return [f for f in FEATURES if f[2] == status]


def tag_html(status: str) -> str:
    cls, label = STATUS_TAG.get(status, ("tag-out", status))
    return f'<span class="tag {cls}">{escape(label)}</span>'
