"""Product implementation status — single source for product_presentation.html (v2.4+)."""

from html import escape

PRODUCT_VERSION = "2.5"
PRODUCT_DATE = "16 июня 2026"
PLAYWRIGHT_PASSED = 30
PLAYWRIGHT_DATE = "2026-06-15"
RUNTIME_GATE_SPEC = "docs/parity/runtime-gate-report.md"

# (id, label, status, note) — status: done | partial | planned | out
FEATURES: tuple[tuple[str, str, str, str], ...] = (
    ("web_client", "Веб-клиент (браузер, PWA)", "done", "parity-matrix: все user-flows covered"),
    ("server", "Сервер и фоновые службы", "done", "core-api, workers, ws-gateway"),
    ("admin", "Админ-консоль (/admin/)", "done", "embedded admin-ui в core-api, не webui app.js"),
    ("auth", "Вход Keycloak (локальные пользователи)", "done", ""),
    ("chats", "Чаты, сообщения, файлы, export", "done", ""),
    ("search_full", "Поиск Solr (Standard/full-server)", "done", ""),
    ("search_pilot", "Поиск SQL (Pilot)", "done", "без Solr; SEARCH_MODE=sql"),
    ("retention", "Ретенция, deep-archive, legal hold", "done", "zstd deep-archive в поставке"),
    ("e2ee", "E2EE / hybrid MLS", "partial", "engineering ✓; prod MLS_STATUS=active после sign-off"),
    ("calls", "Видеозвонки WebRTC (mesh)", "partial", "P2P из чата; TURN Ansible+coturn overlay ✓; deploy на stage — ops"),
    ("push", "Web Push / PWA", "partial", "SW, push-worker, UI; VAPID vault→Ansible→compose ✓"),
    ("tls", "Prod HTTPS / TLS", "partial", "Ansible + preflight scripts ✓; deploy на stage — ops"),
    ("gdpr_export", "Export GDPR completeness", "partial", "export JSON/ZIP ✓; legal checklist — заказчик"),
    ("fr_opt", "Профили Pilot / Standard", "done", "lean Pilot + масштабируемый Standard в поставке"),
    ("fr_opt_dedup", "Дедупликация файлов (FR-OPT-08)", "done", "SHA-256 content_hash, refcount, FILE_DEDUP_ENABLED"),
    ("fr_opt_shard", "Sharding PostgreSQL (FR-OPT-09)", "planned", "scaffold OrganizationShardRouter; full router — ADR"),
    ("load_test", "Formal load test на stage", "partial", "QEMU NT baseline ✓ (docs/benchmarks/qemu-nt-baseline-2026-06-15.json); stage soak — pending"),
    ("bot_api", "Bot API (REST / long-poll)", "planned", "есть MVP webhook worker, не публичный Bot API"),
    ("sso", "SSO Google / LDAP / SAML", "planned", ""),
    ("live", "Live-streaming (HLS)", "planned", "нет кода ingest/HLS"),
    ("mobile", "Мобильные iOS/Android", "out", "вне репозитория"),
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
    <b>Проверено автоматически:</b> Playwright <b>{PLAYWRIGHT_PASSED}/{PLAYWRIGHT_PASSED}</b> на QEMU ({PLAYWRIGHT_DATE}),
    parity user-endpoints covered (spec 002), инженерная приёмка spec 004/006/007.<br/>
    <b>До промышленного запуска (ops):</b> TLS deploy на stage/prod, formal sign-off E2EE, Web Push delivery verify на stage,
    TURN smoke за NAT, load test soak на stage, юридическая политика GDPR export.<br/>
    <b>Не в текущей поставке:</b> мобильные приложения, Live HLS, публичный Bot API, full PG sharding (FR-OPT-09).<br/>
    <span class="small">Источник: <code>{escape(RUNTIME_GATE_SPEC)}</code>,
    <code>deploy/qemu/RESOURCES.md</code>,
    <code>docs/review/e2ee-security-gate-signoff-2026-06-10.md</code>.</span>
  </div>
</div>
"""
