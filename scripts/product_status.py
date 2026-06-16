"""Product implementation status — single source for product_presentation.html."""

from html import escape

PRODUCT_VERSION = "2.5.4"
PRODUCT_DATE = "16 июня 2026"
PLAYWRIGHT_PASSED = 34
PLAYWRIGHT_DATE = "2026-06-16"

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
    ("security_eng", "Безопасность (§24, инженерия)", "done", "headers, rate limit, timing, WS origin; CI security-gate"),
    ("e2ee", "E2EE / hybrid MLS", "partial", "инженерная приёмка ✓; prod sign-off — ops"),
    ("calls", "Видеозвонки WebRTC (mesh)", "partial", "mesh из чата ✓; TURN prod — ops"),
    ("push", "Web Push / PWA", "partial", "UI и worker ✓; prod VAPID — ops"),
    ("tls", "Prod HTTPS / TLS", "partial", "Ansible/TLS в поставке ✓; stage host — с сентября 2026"),
    ("gdpr_export", "Export GDPR completeness", "partial", "export JSON/ZIP + guide ✓; legal strict — ops"),
    ("file_resize", "Миниатюры изображений (/resize)", "done", "embedded в core-api"),
    ("batch_replay", "Batch replay (export-replay)", "done", "JDBC + export_v1; stub отключён в prod compose"),
    ("fr_opt", "Профили Pilot / Standard", "done", "lean Pilot + масштабируемый Standard"),
    ("fr_opt_dedup", "Дедупликация файлов", "done", "одинаковые вложения хранятся один раз"),
    ("fr_opt_shard", "Sharding PostgreSQL", "planned", "Enterprise roadmap"),
    ("load_test", "Formal load test на stage", "partial", "QEMU/k6 scaffold ✓; stage soak — с сентября 2026"),
    ("bot_api", "Bot API (REST L2)", "partial", "long-poll, pin/ban, rotate, rate limit ✓; prod webhook SLA — ops"),
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


def tag_html(status: str) -> str:
    cls, label = STATUS_TAG.get(status, ("tag-out", status))
    return f'<span class="tag {cls}">{label}</span>'


def render_product_snapshot_html() -> str:
    return f"""
<div class="note">
  <div class="req">Состояние продукта на {PRODUCT_DATE}</div>
  <div class="comment">
    <b>Проверено автоматически:</b> Playwright <b>{PLAYWRIGHT_PASSED}/{PLAYWRIGHT_PASSED}</b> на тестовом стенде ({PLAYWRIGHT_DATE});
    PR gate <b>buildIntegrity</b> (spotless ratchet, npm audit, benchmark, unit-тесты).<br/>
    <b>Инженерная безопасность (§24):</b> security headers, rate limit, timing normalization, WebSocket origin — <b>реализовано</b>;
    автоматическая проверка <code>security-gate</code> на тестовом стенде.<br/>
    <b>До промышленного запуска (ops):</b> TLS на stage/prod (стенд с <b>сентября 2026</b>), formal sign-off E2EE, Web Push VAPID prod,
    TURN за NAT на prod, нагрузочный soak на stage, GDPR export strict policy.<br/>
    <b>ФСТЭК / реестр ПО:</b> отдельный организационный трек, <b>без обещания срока</b> в материалах для заказчика.<br/>
    <b>Не в текущей поставке:</b> мобильные приложения, Live §28 (код), sharding PG на Enterprise.
  </div>
</div>
"""
