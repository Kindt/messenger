"""Product implementation status — single source for Block 0 and deck footer."""

from __future__ import annotations

from html import escape

PRODUCT_VERSION = "2.6.2"
PRODUCT_DATE = "18 июня 2026"
DECK_VERSION = "1.1.0"
BUILD_DATE = "2026-06-18"

PRODUCT_STAGE = "working_prototype"
PRODUCT_STAGE_LABEL = "Рабочий прототип"
PRODUCTION_READY = False

PLAYWRIGHT_PASSED = 50
PLAYWRIGHT_TOTAL = 50
PLAYWRIGHT_DATE = "2026-06-18"

PRODUCTION_BLOCKERS: tuple[str, ...] = (
    "Нет промышленного stage/prod стенда (планируется с сентября 2026) — formal load test и ops sign-off отложены",
    "Prod HTTPS/TLS: поставка в Ansible есть, боевые сертификаты и vault — на контуре заказчика",
    "E2EE: инженерная приёмка пройдена; требуется sign-off ИБ перед массовым включением",
    "WebRTC: mesh и LiveKit SFU из чата готовы; TURN в prod-контуре — настройка IT заказчика",
    "Web Push: ui-push Playwright ✓; боевые VAPID на стенде заказчика — ops",
    "Enterprise IdP: admin wizard + SCIM API готовы; live AD/SSO/Kerberos на контуре — ops",
    "Мобильные клиенты iOS/Android — вне текущей поставки",
    "Live-streaming L6 formal 10k soak — LSO-040 (Sep 2026+)",
)

FEATURES: tuple[tuple[str, str, str, str], ...] = (
    ("web_client", "Веб-клиент (браузер, PWA)", "done", "основные пользовательские сценарии"),
    ("server", "Сервер и фоновые службы", "done", ""),
    ("admin", "Админ-консоль (/admin/)", "done", "orgs/users/audit/retention + «Вход / Identity» (auth-policy)"),
    ("auth", "Вход и login-options", "done", "dynamic login, org policy, default org (V042) на multi-org QEMU"),
    ("chats", "Чаты, сообщения, файлы, export", "done", "reply/actions, pin via hex 2b"),
    ("search_full", "Полнотекстовый поиск Solr", "done", "prod full"),
    ("search_sql", "Поиск SQL (fallback)", "done", "dev-min / малый контур"),
    ("retention", "Ретенция, deep-archive, legal hold", "done", "сжатие архива zstd"),
    ("security_eng", "Безопасность (инженерия)", "done", "headers, rate limit, timing, WS origin; CI security-gate"),
    ("e2ee", "E2EE / hybrid MLS", "partial", "инженерная приёмка ✓; prod sign-off — ops"),
    ("calls", "Видеозвонки WebRTC (mesh + LiveKit SFU)", "partial", "mesh + group SFU (livekit-sfu spec) ✓; TURN prod — ops"),
    ("push", "Web Push / PWA", "partial", "UI/worker + tier ui-push ✓; prod VAPID — ops"),
    ("tls", "Prod HTTPS / TLS", "partial", "Ansible/TLS в поставке ✓; stage host — с сентября 2026"),
    ("gdpr_export", "Export GDPR completeness", "partial", "JSON/ZIP + package-manifest.json ✓; legal strict — ops"),
    ("file_resize", "Миниатюры изображений (/resize)", "done", "embedded в core-api"),
    ("batch_replay", "Batch replay (export-replay)", "done", "JDBC + export_v1; stub отключён в prod compose"),
    ("fr_opt", "Dev-min vs prod-full compose", "done", "QEMU dev-min; prod docker-compose.full-server"),
    ("fr_opt_dedup", "Дедупликация файлов", "done", "одинаковые вложения хранятся один раз"),
    ("fr_opt_shard", "Sharding PostgreSQL", "partial", "OrgRoutingFilter + OrganizationRoutingDataSource wired; prod rollout — ADR"),
    ("load_test", "Formal load test на stage", "partial", "QEMU/k6 scaffold ✓; stage soak — с сентября 2026"),
    ("bot_api", "Bot API (REST L2)", "done", "register/webhook/long-poll; webhook outbox retry (V044)"),
    ("plugin_platform", "Платформа ботов-плагинов (L0–L3)", "done", "admin 3-level, bridges, polyglot sidecars; smokes QEMU 6/6"),
    ("sso", "Enterprise IdP (LDAP/SSO/SAML/Kerberos)", "partial", "auth-policy admin + LDAP test + Keycloak sync; live IdP — ops"),
    ("scim", "SCIM 2.0 (Users + Groups)", "partial", "/scim/v2 Users/Groups + bearer token; prod token — ops"),
    ("directory", "Directory sync (LDAP/AD)", "partial", "admin run/status + scheduler (V043); live creds — ops"),
    ("live", "Live-streaming (L2–L5)", "partial", "moderation, HLS, RTMP ingress, ui-live tier ✓; L6 soak — ops"),
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
