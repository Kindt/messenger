"""Product implementation status — single source for Block 0 and deck footer."""

from __future__ import annotations

from html import escape

PRODUCT_VERSION = "0.0.1-SNAPSHOT"
PRODUCT_DATE = "26 июня 2026"
# Deck footer uses PRODUCT_VERSION (not a separate marketing semver).
DECK_VERSION = PRODUCT_VERSION
BUILD_DATE = "2026-06-26"

PRODUCT_STAGE = "working_prototype"
PRODUCT_STAGE_LABEL = "Рабочий прототип"
PRODUCTION_READY = False

# Inventory in tests/e2e-web (`npx playwright test --list`); not a live-stack pass claim.
PLAYWRIGHT_PASSED = 302
PLAYWRIGHT_TOTAL = 302
PLAYWRIGHT_INNER_TIERS = 23
PLAYWRIGHT_DATE = "2026-06-26"
# Last VMA evidence manifest on QEMU lab (spec 029); empty until first green L2/L4 run.
VMA_EVIDENCE_DATE = "2026-06-26"

PRODUCTION_BLOCKERS: tuple[str, ...] = (
    "Нет промышленного стенда: нагрузочные проверки и финальная эксплуатационная приёмка переносятся на контур заказчика",
    "HTTPS/TLS в поставке подготовлен; боевые сертификаты и защищённое хранение секретов настраиваются у заказчика",
    "Сквозное шифрование: инженерная часть готова; перед массовым включением нужна приёмка ИБ",
    "Звонки из чата работают; для сложных корпоративных сетей IT заказчика настраивает медиасервер и правила доступа",
    "Web Push работает в тестовом контуре; боевые ключи уведомлений выпускаются для стенда заказчика",
    "Корпоративный вход и синхронизация пользователей подготовлены; подключение к реальным AD/SSO/Kerberos требует доступа к инфраструктуре заказчика",
    "Внешние базы, хранилища и брокеры описаны через manifests/status/API; реальное подключение выполняется по профилю заказчика",
    "Мобильные клиенты iOS/Android — вне текущей поставки",
    "Массовая проверка live-трансляций на 10 тыс. зрителей отложена до появления подходящего стенда",
)

FEATURES: tuple[tuple[str, str, str, str], ...] = (
    ("web_client", "Веб-клиент (браузер, PWA)", "done", "основные сценарии; адаптив phone/tablet; sidebar unread/@mentions; layout zones"),
    ("ui_layout_zones", "Layout / overlay zones (client+admin)", "done", "webui-build layout-zones + overlay-zones smoke"),
    ("ui_branding", "UI branding / персонализация (027)", "done", "demo palettes + platform/org API + admin + PWA manifest"),
    ("shell_layouts", "Раскладки shell auth/post-login (028)", "done", "default/compact/auth-split + org_slug public branding"),
    (
        "entity_avatars",
        "Аватары сущностей (068)",
        "partial",
        "user/chat/group crop+WS+policy ✓; live QEMU tier soak — guest smokes",
    ),
    ("admin_i18n", "Локализация админ-консоли", "done", "6 locales, nav refresh, tier ui-i18n-artifacts"),
    ("server", "Сервер и фоновые службы", "done", ""),
    ("admin", "Админ-консоль (/admin/)", "done", "orgs/users/audit/retention, auth-policy, fleet, migration, plugins, product-modules, branding"),
    (
        "platform_modules",
        "Product Modules (Base + add-ons)",
        "done",
        "docs/product-modules.yaml, GET /v1/platform/capabilities, admin overrides, ProductComposer",
    ),
    (
        "container_portability",
        "Jakarta EE / WAR portability",
        "done",
        "Tomcat 11 embedded + WAR smoke; docker Gradle settings (spec 021 Phase 7)",
    ),
    ("auth", "Вход и login-options", "done", "dynamic login, org policy, default org (V042)"),
    ("chats", "Чаты, сообщения, файлы, export", "done", "reply/actions/pin, threads, @mentions, voice, location/contact cards"),
    ("mentions", "@mentions + push", "done", "V047, NATS msg.mention, push «Mention in…», webui highlight"),
    ("read_receipts", "Read receipts (доставлено/прочитано)", "done", "WS events + overlay «кто прочитал» в групповых чатах"),
    ("threads", "Треды обсуждений", "done", "V046 thread_id, export thread_id, webui discussion panel"),
    ("voice_msgs", "Голосовые сообщения", "done", "V048 duration_ms, audio bubble, smokes"),
    ("channels", "Каналы broadcast", "done", "type channel, read-only post для участников"),
    ("link_preview", "Превью ссылок", "partial", "preview worker + card ✓; ADR SSRF ✅; prom egress — LSO-065"),
    ("smartapps_ui", "SmartApps launcher (web)", "done", "marketplace API + search/categories + vitrine tiles"),
    ("message_edit", "Редактирование сообщений", "done", "PATCH + webui + Playwright ui-messaging"),
    ("migration_import", "Импорт переписки (Telegram)", "done", "scheduler + admin UI + smoke; prod volume playbook — ops"),
    ("archive_folders", "Папки, архив, статус, DND", "done", "sidebar folders/archive, custom status, DND schedule"),
    ("ip_allowlist", "IP allowlist организации", "done", "admin API + app-layer enforce (lab); edge prod — ops"),
    ("fleet_ops", "Fleet snapshot / observability", "partial", "admin fleet grid + fleet-lab runbook ✓; prom Grafana — LSO-077"),
    ("search_full", "Полнотекстовый поиск Solr", "done", "prod full"),
    ("search_sql", "Поиск SQL (fallback)", "done", "dev-min / малый контур"),
    ("retention", "Ретенция, deep-archive, legal hold", "done", "сжатие архива zstd"),
    ("security_eng", "Безопасность (инженерия)", "done", "headers, rate limit, timing, WS origin; CI security-gate"),
    ("e2ee", "E2EE / hybrid MLS + OpenMLS", "partial", "wire parity + *Mls* tests ✓; sign-off ИБ — LSO-015/016"),
    ("calls", "Видеозвонки WebRTC (mesh + LiveKit SFU)", "partial", "mesh + SFU ✓; запись mesh-звонка lab ✓; TURN prod + масштаб — ops"),
    ("push", "Web Push / PWA", "partial", "UI/worker + tier ui-push ✓; prod VAPID — ops"),
    ("tls", "Prod HTTPS / TLS", "partial", "Ansible/TLS в поставке ✓; stage host — с сентября 2026"),
    ("gdpr_export", "Export GDPR completeness", "partial", "JSON/ZIP + package-manifest.json ✓; legal strict — ops"),
    ("file_resize", "Миниатюры изображений (/resize)", "done", "embedded в core-api"),
    ("batch_replay", "Batch replay (export-replay)", "done", "JDBC + export_v1; stub отключён в prod compose"),
    ("fr_opt", "Dev-min vs prod-full compose", "done", "docker-compose.dev-min; prod docker-compose.full-server"),
    ("fr_opt_dedup", "Дедупликация файлов", "done", "одинаковые вложения хранятся один раз"),
    ("fr_opt_shard", "Sharding PostgreSQL", "partial", "OrgRouting wired; prod rollout ADR ✅; prom soak — Sep 2026+"),
    (
        "federation_scaffold",
        "Federation scaffold",
        "done",
        "trust registry + admin UI + member guard; GET /v1/platform/federation/status",
    ),
    ("load_test", "Formal load test на stage", "partial", "k6 scaffold + lab baseline ✓; stage soak — с сентября 2026"),
    ("bot_api", "Bot API (REST L2)", "done", "register/webhook/long-poll; webhook outbox retry (V044)"),
    ("plugin_platform", "Платформа ботов-плагинов (L0–L3)", "done", "admin 3-level, bridges, vitrine, polyglot sidecars"),
    (
        "m365_integration_pack",
        "M365/Exchange pack (G-SUPER-01–03)",
        "partial",
        "014 policy ✅; exchange-bridge L1 calendar ✓; storage-bridge L1 ✓; mail L0 OWA link; live Graph — LSO-030",
    ),
    ("sso", "Enterprise IdP (LDAP/SSO/SAML/Kerberos)", "partial", "auth-policy admin + LDAP test + Keycloak sync; live IdP — ops"),
    ("scim", "SCIM 2.0 (Users + Groups)", "partial", "/scim/v2 Users/Groups + bearer token; prod token — ops"),
    ("directory", "Directory sync (LDAP/AD)", "partial", "admin run/status + scheduler (V043); live creds — ops"),
    ("live", "Live-streaming (L2–L5)", "partial", "moderation, HLS, RTMP ingress, ui-live tier ✓; L6 soak — ops"),
    ("mobile", "Мобильные iOS/Android", "out", "вне текущей поставки"),
    ("desktop", "Desktop-клиент", "out", "отдельный проект"),
    ("dlp", "DLP", "partial", "mock L2 bridge dlp-mock + pre-send gate ✓; live vendor — LSO-067"),
    ("chat_polls", "Опросы в чате", "done", "REST create/vote/close + webui panel + Playwright tier"),
    ("scheduled_send", "Отложенная отправка", "done", "scheduler + POST + composer modal"),
    ("message_reminders", "Напоминания о сообщении", "done", "API + scheduler + message action + settings list"),
    ("offline_web", "Offline web (IndexedDB)", "done", "ui-offline-cache.js + offline/cached thread banner"),
    (
        "external_stack",
        "Кастомизируемый внешний стек (023)",
        "partial",
        "desired/observed manifests, attached probes, compatibility pack catalog, Search SPI, cutover reports, admin preflight ✓; live BYO cutover — по профилю заказчика",
    ),
    ("phase5_kanban", "Kanban in chat (ADR)", "done", "REST PATCH/DELETE + webui move/delete + Playwright tier"),
    ("phase5_whiteboard", "Whiteboard (ADR)", "done", "REST + canvas/JSON editor + Playwright tier"),
    ("phase5_conf_adr", "Conference ADR (record/guest/breakout)", "done", "REST + guest waiting/admit + recording complete + captions; prom record — LSO-068/070"),
    ("phase5_sip_passkeys", "SIP / passkeys scaffold", "done", "REST + settings SIP/passkey scaffold UX; live WebAuthn — LSO-073/076"),
    ("phase5_stickers", "Stickers / GIF (ADR)", "done", "REST + packs/GIF preview + marketplace connect UX"),
    ("phase5_ai", "AI chat assist (ADR)", "done", "POST /ai/assist + webui overlay; live gateway — customer profile"),
    ("fstec", "ФСТЭК / реестр", "planned", "charter draft; expert review LSO-071"),
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
