"""Product implementation status — single source for product_presentation.html."""

from html import escape

PRODUCT_VERSION = "2.6.2"
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

# Оговорки §4 / §8 — кратко (для таблиц) и развёрнуто (для бухгалтерии / закупки)
CAPABILITY_QUAL_NOTES: dict[str, str] = {
    "calls": "Нужен сервер ретрансляции видео (TURN) в контуре заказчика — см. §3.2 (NAT, firewall). Настраивает IT при вводе в эксплуатацию.",
    "e2ee": "Нужна письменная приёмка (sign-off) ИБ и руководства перед массовым включением — см. §3.2.",
    "push": "Нужны боевые ключи VAPID и проверка уведомлений на стенде заказчика — см. §3.2 (ops).",
    "tls": "Нужны сертификаты HTTPS, DNS и хранилище секретов на контуре заказчика — см. §3.2 (ops).",
    "sso": "Шаблоны есть; подключение корпоративного входа (SSO/LDAP) — работа IT заказчика — см. §3.2.",
    "live": "Массовые трансляции (all-hands) — отдельный этап roadmap, не входят в текущую поставку.",
}

CAPABILITY_QUAL_NOTES_PLAIN: dict[str, str] = {
    "calls": (
        "Программа для звонков из чата <b>готова</b>. Чтобы звонки стабильно работали из дома, филиалов и "
        "офисов с жёсткой сетевой защитой, IT заказчика устанавливает отдельный сервер ретрансляции видео (TURN). "
        "Без него часть пользователей «за NAT» или за межсетевым экраном (firewall) не сможет соединиться — "
        "это не ошибка мессенджера, а требование корпоративной сети."
    ),
    "e2ee": (
        "Усиленное шифрование переписки (E2EE) <b>реализовано и проверено инженерами</b>. "
        "Массовое включение для всех сотрудников — после <b>sign-off</b> (письменной приёмки службы безопасности "
        "и руководства): типичный корпоративный шаг, не связанный с оплатой лицензии."
    ),
    "push": (
        "Уведомления в браузере и установка «как приложение» (PWA) <b>есть в продукте</b>. "
        "Для промышленного контура IT выпускает служебные ключи (VAPID) и проверяет, что push доходит до сотрудников — "
        "работа эксплуатации (ops), обычно 1–2 дня на стенде."
    ),
    "tls": (
        "Инструкции по защищённому доступу (HTTPS) <b>входят в поставку</b>. "
        "Сами SSL-сертификаты, привязка доменного имени (DNS) и хранение паролей (vault) оформляет IT или "
        "подрядчик на инфраструктуре заказчика — как для любого корпоративного сайта."
    ),
    "sso": (
        "Вход по логину/паролю работает. «Войти через корпоративный портал» (SSO) и учётки из Active Directory (LDAP) — "
        "подключает IT заказчика по готовым шаблонам; срок зависит от вашего IdP, не от разработки чатов."
    ),
    "live": (
        "Онлайн-трансляции на сотни и тысячи зрителей (all-hands, обучение) <b>не входят</b> в текущую версию; "
        "запланированы отдельным этапом roadmap (media stack). Базовый мессенджер и видеозвонки в чате — без этого."
    ),
}


def capability_qual_html(key: str | None) -> str:
    if not key or key not in CAPABILITY_QUAL_NOTES:
        return "—"
    return CAPABILITY_QUAL_NOTES[key]


def capability_qual_plain_html(key: str | None) -> str:
    if not key or key not in CAPABILITY_QUAL_NOTES_PLAIN:
        return "—"
    return CAPABILITY_QUAL_NOTES_PLAIN[key]


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
    <b>До промышленного запуска (эксплуатация, ops):</b> защищённый HTTPS на prod/stage, приёмка E2EE (sign-off),
    push-ключи VAPID, сервер ретрансляции видео (TURN) для звонков из сетей с NAT/firewall, нагрузочный soak на stage,
    политика export для GDPR — см. расшифровку в <b>§3.2</b> и сноски <a href="#fn-legend">†…⊕</a>.<br/>
    <b>ФСТЭК / реестр ПО:</b> отдельный организационный трек, <b>без обещания срока</b> в материалах для заказчика.<br/>
    <b>Не в текущей поставке:</b> мобильные приложения, Live §28 (код), sharding PG на Enterprise.<br/>
    <b>Платформа ботов-плагинов (L0–L3):</b> админка preset/policy/instance, bridges (Exchange, 1С, storage, OCR/AI demo),
    polyglot sidecars (PHP/Go/Java8/VB.NET/PowerShell); узел интеграций отдельно от сервера чатов — <b>реализовано</b>, smokes на тестовом стенде.
  </div>
</div>
"""
