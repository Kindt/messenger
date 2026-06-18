"""Persona draft content — specialist voice, facts from product_status and compare."""

from __future__ import annotations

from html import escape

from scripts.presentation import product_status as ps
from scripts.presentation.data_loader import load_competitors, load_offerings

PERSONA_VOICE = {
    "pm": "PM / business analyst",
    "tech": "DevOps + backend",
    "sales": "Presales",
    "user": "Office employee",
}

USER_JARGON_DENY = ("JWT", "Keycloak", "NATS", "Solr", "mesh")


def _status_link() -> str:
    return '<p class="status-link"><a href="#block-0">↑ см. статус прототипа</a></p>'


def draft_pm_s1() -> str:
    done = len(ps.features_by_status("done"))
    partial = len(ps.features_by_status("partial"))
    return _status_link() + f"""
<p>Korus Messenger — {escape(ps.PRODUCT_STAGE_LABEL.lower())}: traceability к ТЗ, инженерная приёмка Playwright {ps.PLAYWRIGHT_PASSED}/{ps.PLAYWRIGHT_TOTAL}.</p>
<p>В scope: {done} реализованных модулей, {partial} частичных (ops-dependent). Out of scope до Sep 2026: stage/prod soak, formal sign-off.</p>
<ul>
<li>Traceability: чаты, export, retention, bot platform — done</li>
<li>Roadmap: sharding PG, Live §28 — planned</li>
<li>Ops tail: TLS, E2EE sign-off, TURN — deferred (spec 015)</li>
</ul>
"""


def draft_pm_s2() -> str:
    data = load_competitors()
    tiers = {}
    for p in data["products"]:
        tiers.setdefault(p["tier"], []).append(p["label"])
    rows = "".join(
        f"<li><b>Уровень {escape(t)}:</b> {escape(', '.join(labels))}</li>"
        for t, labels in sorted(tiers.items())
    )
    return f"""
<p>Матрица 11 продуктов по tier A/B/C — без якорных точек сравнения.</p>
<ul>{rows}</ul>
<p>Публичных тарифных строк в offerings: {len(load_offerings())}.</p>
"""


def draft_pm_s3() -> str:
    blockers = "".join(f"<li>{escape(b)}</li>" for b in ps.PRODUCTION_BLOCKERS[:5])
    return f"""
<p>Сравнение по тарифам конкурентов @ их RU; headroom Korus — см. таблицу ниже.</p>
<div class="blocker-list"><h4>Блокеры production (severity)</h4><ul>{blockers}</ul></div>
<p class="small">Методология: METRIC_POLICY v1 — только registered_users для TCO.</p>
"""


def draft_pm_s4() -> str:
    return """
<p>Калькулятор поддержки: введите число рег. пользователей, SLA и опции обновлений.</p>
<p class="small">FTE = 0,15 + RU/80 000 (cap 4,0); SLA 24×7 ×2,5.</p>
"""


def draft_tech_s1() -> str:
    return _status_link() + """
<p>Java 25 monolith + workers: PostgreSQL, NATS, Redis, Solr (Standard), MinIO, Keycloak, ws-gateway.</p>
<p>Deploy profiles: Pilot (lean, SQL search), Standard (Solr HA, replica PG). Логическая схема — прототип на лабораторном dev-стенде<sup>†</sup>.</p>
<p class="footnote"><sup>†</sup> QEMU — footnote для разработчиков, не customer-facing runtime.</p>
"""


def draft_tech_s2() -> str:
    return """
<p>Фокус: on-prem sizing, ops transparency, публичные прайсы где есть.</p>
<p>Tier B/C: Loop, Rocket.Chat, Mattermost — concurrent vs registered — не смешиваем в TCO v1.</p>
"""


def draft_tech_s3() -> str:
    return """
<p>18 критериев — qualitative matrix; sizing @ RU конкурента + headroom chip.</p>
<p>RAM bar строится по профилю Korus, покрывающему RU строки конкурента.</p>
"""


def draft_tech_s4() -> str:
    return """
<p>Калькулятор мощностей: произвольный RU → RAM, узлы, headroom профиля.</p>
"""


def draft_sales_s1() -> str:
    return _status_link() + """
<p>Value: on-prem контур, compliance-ядро (export, dual-TTL), bot platform L0–L3.</p>
<p>Deployment: Pilot (10k рег.), Standard (100k), Enterprise roadmap — pilot ≠ production.</p>
<p>Cells (spec 011): Phase 0–1 engineering closed; ops → Sep 2026+.</p>
"""


def draft_sales_s2() -> str:
    data = load_competitors()
    items = [p for p in data["products"] if p["id"] != "korus"]
    lis = "".join(
        f'<li>{escape(p["label"])} — tier {escape(p["tier"])}</li>' for p in items
    )
    return f"<ul>{lis}</ul>"


def draft_sales_s3() -> str:
    return """
<p>TCO-таблица: одна строка = публичный тариф конкурента. Источник — кликабельный URL.</p>
<p class="small">Строки без публичной цены — «цена по запросу», TCO compare пропущен.</p>
"""


def draft_sales_s4() -> str:
    return """
<p>Калькулятор TCO: введите RU → ₽/мес и ₽/год infra Korus (не лицензия вендора).</p>
"""


def draft_user_s1() -> str:
    return _status_link() + """
<p>Корпоративный мессенджер — как чат в телефоне, но для работы: переписка, файлы, звонки.</p>
<p>Данные остаются у вашей компании. Администратор видит, кто что отправил — для порядка и аудита.</p>
<p>Сейчас это рабочий прототип: основные сценарии работают на тестовом стенде.</p>
"""


def draft_user_s2() -> str:
    return """
<p>Есть облачные сервисы и решения «у себя в офисе». Korus — для организаций, где важны контроль данных и экспорт.</p>
<p>Мобильные приложения из магазина — пока не в поставке; браузер и установка «как приложение» — да.</p>
"""


def draft_user_s3() -> str:
    return """
<p>Удобство: поиск по переписке, файлы в чате, звонок одной кнопкой.</p>
<p>Частично: уведомления на телефон — нужна настройка IT; усиленное шифрование — после согласования с безопасностью.</p>
"""


def draft_user_s4() -> str:
    return """
<p>Мастер сценариев, FAQ и короткий тур — без технических терминов.</p>
<p>FAQ: Telegram/WhatsApp — удобны, но корпоративный аудит и хранение по политике компании — в Korus.</p>
"""


DRAFT_MAP = {
    "pm": (draft_pm_s1, draft_pm_s2, draft_pm_s3, draft_pm_s4),
    "tech": (draft_tech_s1, draft_tech_s2, draft_tech_s3, draft_tech_s4),
    "sales": (draft_sales_s1, draft_sales_s2, draft_sales_s3, draft_sales_s4),
    "user": (draft_user_s1, draft_user_s2, draft_user_s3, draft_user_s4),
}
