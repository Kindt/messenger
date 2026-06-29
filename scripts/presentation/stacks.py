"""Стек по узлам, стеки конкурентов, компетенции, плагины L0–L3 — для Tech-вкладки deck."""

from __future__ import annotations

from html import escape

from .data_loader import load_competitors
from . import anchors as anc
from . import product_status as ps

# Узлы production full (docker-compose.full-server.yml)
NODE_STACK: tuple[tuple[str, str, str], ...] = (
    (
        "Web / LB",
        "nginx или korus-web (Tomcat), статика webui",
        "Tomcat 11, vanilla JS webui, TLS termination",
    ),
    (
        "App (core-api)",
        "REST API, админка, auth policy, E2EE/MLS orchestration",
        "Java 25, Jersey 4, embedded Tomcat, Flyway, Nimbus JWT",
    ),
    (
        "WS gateway",
        "WebSocket клиентов, fan-out событий",
        "Java, отдельный контейнер ws-gateway",
    ),
    (
        "Workers",
        "message-pipeline (ядро), retention, export-replay, deep-archiver, push/preview/bot-delivery, indexer",
        "Java workers / NATS consumers; в калькуляторе — отдельные строки по compose profile",
    ),
    (
        "Indexer (hot-plug)",
        "Solr indexing, опциональный вынос",
        "Java services/indexer, ADR hot-plug",
    ),
    (
        "PostgreSQL hot",
        "OLTP: чаты, сообщения, orgs, auth policy",
        "PostgreSQL 16",
    ),
    (
        "PostgreSQL archive",
        "deep-archive, compliance export (profile full)",
        "PostgreSQL 16",
    ),
    (
        "NATS",
        "async bus, JetStream",
        "NATS 2.10",
    ),
    (
        "Redis",
        "кэш, сессии, rate limits",
        "Redis 7",
    ),
    (
        "MinIO",
        "object storage: файлы, вложения",
        "S3-compatible",
    ),
    (
        "Solr + ZooKeeper",
        "полнотекстовый поиск (profile full/solr)",
        "Solr, ZooKeeper 3.9",
    ),
    (
        "Keycloak",
        "IdP, OIDC/SAML, realm per org",
        "Keycloak 24",
    ),
    (
        "Integrations node",
        "L1–L3 плагины, bot-delivery, внешние API",
        "отдельная VM/контейнер; код плагина не в JVM core-api",
    ),
    (
        "LiveKit (опц.)",
        "WebRTC SFU для групповых звонков; отдельная строка в калькуляторе infra",
        "LiveKit server; mesh работает без SFU",
    ),
)

COMPETENCIES: tuple[tuple[str, str, str], ...] = (
    (
        "Backend (Java)",
        "Java 25, REST/Jersey 4, SQL/Flyway, NATS, unit/H2-тесты",
        "core-api, workers, контракты API/NATS",
    ),
    (
        "Frontend (webui)",
        "Vanilla JS, WebSocket, i18n, Playwright, E2EE WASM hooks",
        "web-client, inner tier acceptance",
    ),
    (
        "DevOps / SRE",
        "Docker Compose, Ansible, VM sizing, Prometheus, backup/DR",
        "развёртывание prod-full, мониторинг, VM sizing",
    ),
    (
        "Интегратор L1–L2",
        "HTTP/webhook, LDAP/AD, Keycloak, bot API, observability плагина",
        "узел integrations, FR-INT-09/10",
    ),
    (
        "ИБ / комплаенс",
        "E2EE/MLS review, audit, retention/export, auth policy",
        "заказчик enterprise-auth, deep-archive",
    ),
    (
        "L1 поддержка",
        "runbook, эскалация, RU/режим поддержки из калькулятора",
        "первая линия по договору сопровождения",
    ),
    (
        "QA",
        f"JUnit, {ps.PLAYWRIGHT_TOTAL} Playwright-сценариев, {ps.PLAYWRIGHT_INNER_TIERS} inner-тиров, "
        "VPP comprehensive gates (~146), data-driven ui-tests, smoke/QEMU",
        "US9 inner/outer gate",
    ),
)

PLUGIN_TIERS: tuple[tuple[str, str, str, str], ...] = (
    (
        "L0",
        "Встроенные интеграции",
        "LDAP sync, Keycloak, стандартные webhook — в составе продукта",
        "Без отдельного узла; конфиг + core-api",
    ),
    (
        "L1",
        "Лёгкий плагин",
        "HTTP in/out, cron, маппинг полей; ~512 МБ RAM / 0.25 vCPU",
        "Узел integrations; не hot-path сообщений",
    ),
    (
        "L2",
        "Средний плагин",
        "Очереди, state, несколько API; ~2 ГБ / 1 vCPU",
        "Изолированный процесс; SLA отдельно от core",
    ),
    (
        "L3",
        "Тяжёлый / custom",
        "Своя БД, ML, legacy ERP; ~8+ ГБ / 4+ vCPU",
        "Отдельная VM или cell; граница — не блокировать message pipeline",
    ),
)

EXTERNAL_STACK_PROFILES: tuple[tuple[str, str, str, str], ...] = (
    (
        "Поставляемый стек",
        "PG 16, MinIO, NATS 2.10, Redis 7, Keycloak 24, Tomcat/nginx",
        "Описан и собирается полный состав промышленного профиля; эксплуатационная приёмка и нагрузочная проверка выполняются отдельно.",
        "Готово: описание желаемого/фактического состояния, проверки подключения, compatibility packs, статус каталога, готовность профиля, контракты компонентов и preflight в админке.",
    ),
    (
        "Стек заказчика",
        "PostgreSQL/S3/NATS/OIDC/Redis/web-edge в контуре заказчика",
        "Поддерживается через явное описание целевого состояния и границ ответственности; секреты остаются в защищённом хранилище заказчика.",
        "Готово в repo-local контуре: проверка готовности перехода, исправления по компонентам, preflight профиля, статусы ok/warning/blocked, сводный отчёт, рекомендации по исправлению, предупреждения по контрактам и проверка drift.",
    ),
    (
        "Кандидаты на замену",
        "Postgres Pro/Tantor, Arenadata/Jatoba/Pangolin, Angie, VKS, DLP vendors, OpenSearch/Elasticsearch",
        "Не считается поддержанным стеком до оценки влияния, лицензий, проверки ИБ и подтверждения производительности.",
        "Готово: контракты кандидатов, режимы unsupported, защита от случайного выбора; осталось проверить в лаборатории с вендором, юристами и лицензиями.",
    ),
    (
        "Отложенная live-проверка",
        "Реальный домен, защищённое хранилище заказчика, боевые доступы к внешним системам, формальная приёмка",
        "Не входит в repo-local инженерную волну; хранится в отложенном ops-реестре.",
        "Spec 015 / Sep 2026+: подключение реального стека заказчика, нагрузочная проверка, TLS/vault/vendor-приёмка.",
    ),
)


def render_node_stack_table() -> str:
    rows = "".join(
        f"<tr><td><strong>{escape(n)}</strong></td><td>{escape(r)}</td><td>{escape(s)}</td></tr>"
        for n, r, s in NODE_STACK
    )
    return f"""
<div class="stack-block" id="tech-stack-nodes">
  <h4>Стек по узлам (Korus)</h4>
  <p class="footnote">Полный промышленный состав — все роли из compose. Dev-min (QEMU) объединяет роли на одной VM для разработки.</p>
  <div class="table-wrap"><table class="feature-table">
    <thead><tr><th>Узел / роль</th><th>Назначение</th><th>Стек</th></tr></thead>
    <tbody>{rows}</tbody>
  </table></div>
</div>"""


def render_external_stack_profiles() -> str:
    rows = "".join(
        f"<tr><td><strong>{escape(kind)}</strong></td><td>{escape(profiles)}</td>"
        f"<td>{escape(boundary)}</td><td>{escape(next_steps)}</td></tr>"
        for kind, profiles, boundary, next_steps in EXTERNAL_STACK_PROFILES
    )
    return f"""
<div class="stack-block" id="tech-external-stack-profiles">
  <h4>Кастомизируемый внешний стек (spec 023)</h4>
  <p class="footnote">Статус честный: реализован локальный слой проверки и диагностики, а не автоматический перенос промышленного контура на любого поставщика.</p>
  <div class="table-wrap"><table class="feature-table">
    <thead><tr><th>Профиль</th><th>Примеры</th><th>Граница поддержки</th><th>Доработки</th></tr></thead>
    <tbody>{rows}</tbody>
  </table></div>
</div>"""


def render_competitor_stacks_table() -> str:
    data = load_competitors()
    products = data.get("products", [])
    korus_ops = "Java modular monolith + workers; PG 16, NATS, Redis, MinIO, Solr, Keycloak"
    rows = ""
    for p in products:
        if p.get("id") == "korus":
            continue
        ops = (p.get("features") or {}).get("ops", "—")
        rows += (
            f"<tr><td>{escape(p.get('label', p.get('id', '')))}</td>"
            f"<td>{escape(str(ops))}</td>"
            f"<td>{escape(p.get('deployment', '—'))}</td></tr>"
        )
    return f"""
<div class="stack-block" id="tech-competitor-stacks">
  <h4>Стеки конкурентов (ops)</h4>
  <p class="footnote">Поле <code>ops</code> из реестра конкурентов — для сравнения архитектурной сложности сопровождения,
  не для «лучше/хуже». Korus: <strong>{escape(korus_ops)}</strong>.</p>
  <div class="table-wrap"><table class="feature-table">
    <thead><tr><th>Продукт</th><th>Ops / стек (кратко)</th><th>Развёртывание</th></tr></thead>
    <tbody>{rows}</tbody>
  </table></div>
</div>"""


def render_competencies_table() -> str:
    rows = "".join(
        f"<tr><td><strong>{escape(role)}</strong></td><td>{escape(skills)}</td><td>{escape(why)}</td></tr>"
        for role, skills, why in COMPETENCIES
    )
    return f"""
<div class="stack-block" id="tech-competencies">
  <h4>Компетенции для развития и поддержки</h4>
  <p class="footnote">Минимальный набор ролей для in-house команды или аутстаффа; объём FTE — в {anc.link(anc.SALES_CALC, "калькуляторе сопровождения")}.</p>
  <div class="table-wrap"><table class="feature-table">
    <thead><tr><th>Роль</th><th>Навыки</th><th>Зона ответственности</th></tr></thead>
    <tbody>{rows}</tbody>
  </table></div>
</div>"""


def render_plugin_platform() -> str:
    rows = "".join(
        f"<tr><td><strong>{escape(t)}</strong></td><td>{escape(title)}</td>"
        f"<td>{escape(desc)}</td><td>{escape(boundary)}</td></tr>"
        for t, title, desc, boundary in PLUGIN_TIERS
    )
    return f"""
<div class="stack-block" id="tech-plugins">
  <h4>Гибкие плагины L0–L3 и границы применения</h4>
  <p class="footnote">Контракты FR-INT-09/10; код плагина <strong>не</strong> загружается в JVM <code>core-api</code> — только узел integrations / sidecar.</p>
  <div class="table-wrap"><table class="feature-table">
    <thead><tr><th>Уровень</th><th>Тип</th><th>Описание</th><th>Граница</th></tr></thead>
    <tbody>{rows}</tbody>
  </table></div>
  <ul class="footnote stack-notes">
    <li>Hot-path (отправка сообщений, WS, retention) — только core + workers.</li>
    <li>L1–L3 добавляют RAM/vCPU на узел integrations; учитывать в {anc.link(anc.TECH_SIZING, "калькуляторе sizing")}.</li>
    <li>L3 и custom — отдельный cell/VM при риске noisy neighbor.</li>
  </ul>
</div>"""
