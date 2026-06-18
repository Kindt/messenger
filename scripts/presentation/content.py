"""Persona draft content — specialist voice, facts from product_status and compare."""

from __future__ import annotations

from html import escape

from scripts.presentation import product_status as ps
from scripts.presentation.data_loader import load_competitors
from scripts.presentation import anchors as anc

PERSONA_VOICE = {
    "pm": "PM / business analyst",
    "tech": "DevOps + backend",
    "sales": "Presales",
    "user": "Office employee",
}

USER_JARGON_DENY = ("JWT", "Keycloak", "NATS", "Solr", "mesh")

TAB_INTRO = {
    "pm": "Для руководителя проекта и аналитика: возможности, риски, сравнение с рынком.",
    "tech": "Для DevOps и разработки: архитектура, sizing по числу рег., калькулятор infra.",
    "sales": "Для presales: ценность, TCO по публичным тарифам конкурентов и трём облакам.",
    "user": "Для сотрудника офиса: простым языком, без технических терминов.",
}

SECTION_TITLES = {
    "pm": ("Что умеет продукт", "Пересечение с конкурентами", "TCO (кратко)", "Сопровождение"),
    "tech": ("Архитектура", "Конкуренты", "Матрица и мощности", "Калькулятор infra"),
    "sales": ("Ценность", "Конкуренты", "TCO", "Калькулятор infra"),
    "user": ("Зачем нужен", "Альтернативы", "Удобство", "Вопросы и сценарии"),
}


def _status_link() -> str:
    return '<p class="status-link"><a href="#block-0">Статус продукта ↑</a></p>'


def draft_pm_s1() -> str:
    return _status_link() + f"""
<p><strong>Korus Messenger</strong> — корпоративный мессенджер для переписки, файлов, звонков и администрирования в контуре заказчика.</p>
<p>Девять блоков возможностей — в карточках ниже. Сводный статус и blockers — в <a href="#block-0">шапке презентации</a>.</p>
"""


def draft_pm_s2() -> str:
    data = load_competitors()
    n = len(data["products"])
    return f"""
<p>Сравнение с <strong>{n} продуктами</strong> рынка РФ (tier A–C) по типовому ТЗ заказчика.</p>
<p>Сначала — реестр конкурентов и различия; затем матрица по 8 критериям. TCO — {anc.link(anc.SALES_TCO, "вкладка «Продажная», раздел TCO")}.</p>
"""


def draft_pm_s3() -> str:
    blockers = "".join(f"<li>{escape(b)}</li>" for b in ps.PRODUCTION_BLOCKERS[:5])
    return f"""
<p>TCO: лицензия конкурента vs infra Korus на том же числе рег. Полная таблица — {anc.link(anc.SALES_TCO, "TCO на вкладке «Продажная»")}.</p>
<div class="callout callout-warn">
<h4>Что мешает промышленному запуску</h4>
<ul class="bullet-clean">{blockers}</ul>
</div>
"""


def draft_pm_s4() -> str:
    return f"""
<p><strong>Сопровождение</strong> — стоимость команды (FTE), не серверов. Расчёт infra — {anc.link(anc.TECH_SIZING, "калькулятор infra (техническая вкладка)")}.</p>
<p class="small">Модель упрощённая: RU, режим поддержки, релизы, топология, интеграции и модель команды — в калькуляторе; остальное — в {anc.link(anc.PM_SUPPORT, "разделе «Сопровождение»")}.</p>
"""


def draft_tech_s1() -> str:
    return _status_link() + """
<p>Модульный монолит Java 25: API, фоновые воркеры, WebSocket-шлюз. Ниже — <a href="#tech-stack-nodes">стек по узлам</a> и
<a href="#tech-competencies">компетенции</a> команды.</p>
<p class="footnote">Лабораторный стенд — для приёмки функций, не для нагрузочного soak-теста.</p>
"""


def draft_tech_s2() -> str:
    data = load_competitors()
    n = len(data["products"])
    return f"""
<p>Реестр <strong>{n}</strong> продуктов: tier, deploy, offerings. Ниже — <a href="#tech-competitor-stacks">сравнение ops-стеков</a> (Kafka vs NATS и т.д.).</p>
<p class="small">Concurrent vs registered users не смешиваются в TCO v1.</p>
"""


def draft_tech_s3() -> str:
    return f"""
<p>RAM-бар — суммарная RAM <strong>prod full</strong> при RU строки сравнения ({anc.link(anc.TECH_SIZING, "методика sizing")}). Ниже — {anc.link(anc.TECH_PLUGINS, "плагины L0–L3")} и матрица критериев.</p>
"""


def draft_tech_s4() -> str:
    return """
<p>Два независимых калькулятора: <strong>слева</strong> — предел в пользователях (от модулей); <strong>справа</strong> — ресурсы под нагрузку и смета infra.</p>
<p class="footnote">Prod full (<code>docker-compose.full-server.yml</code>). Dev-min — только QEMU.</p>
"""


def draft_sales_s1() -> str:
    return _status_link() + """
<div class="card-grid card-grid-3">
<div class="card"><h4>On-prem</h4><p>Данные в контуре заказчика. Export, dual-TTL, legal hold.</p></div>
<div class="card"><h4>Масштабирование</h4><p>Рост от тысяч до сотен тысяч рег. без смены продукта.</p></div>
<div class="card"><h4>Платформа ботов</h4><p>L0–L3: меню, webhook, интеграции через sidecar-мосты.</p></div>
</div>
<p class="small">Цены infra — ориентиры по REG.RU, Yandex Cloud, Timeweb; не коммерческое предложение.</p>
"""


def draft_sales_s2() -> str:
    data = load_competitors()
    n = len([p for p in data["products"] if p["id"] != "korus"])
    return f"""
<p>Реестр <strong>{n + 1}</strong> продуктов (включая Korus): tier, модель развёртывания, число тарифных строк в TCO.</p>
"""


def draft_sales_s3() -> str:
    return """
<p>Каждая строка — публичный тариф конкурента на <strong>своё</strong> число RU. Infra Korus пересчитывается под это RU; колонки RAM / тир VM / vCPU показывают, почему compute может не расти (plateau на минимальном prod full).</p>
<p class="small">Нет публичной цены — «по запросу». Мелкий on-prem может быть дороже облачного тарифа — это честное сравнение.</p>
"""


def draft_sales_s4() -> str:
    return """
<p>Калькулятор: число рег. → смета infra (REG.RU, Yandex Cloud, Timeweb) с разбивкой и ссылками на прайсы.</p>
"""


def draft_user_s1() -> str:
    return """
<p>Корпоративный мессенджер — как привычный чат, но для работы: переписка с коллегами, файлы, звонок из чата.</p>
<p>Данные хранятся у вашей компании. Администратор может посмотреть переписку по правилам компании — для порядка и проверок.</p>
<p>Сейчас это рабочий прототип: основные сценарии уже можно попробовать на тестовом стенде.</p>
"""


def draft_user_s2() -> str:
    return """
<p>Есть облачные сервисы и решения «сервер у себя». Korus — когда важны контроль данных и выгрузка для аудита.</p>
<p>Отдельного приложения в App Store пока нет — достаточно браузера или «добавить на рабочий стол».</p>
"""


def draft_user_s3() -> str:
    return """
<p>Удобно: поиск по переписке, файлы в чате, звонок одной кнопкой.</p>
<p>Частично готово: уведомления на телефон настраивает IT; усиленное шифрование — после согласования с безопасностью.</p>
"""


def draft_user_s4() -> str:
    return """
<p>Девять типовых сценариев для онбординга сотрудников и блок частых вопросов — материал презентации, не экран справки в продукте.</p>
"""


DRAFT_MAP = {
    "pm": (draft_pm_s1, draft_pm_s2, draft_pm_s3, draft_pm_s4),
    "tech": (draft_tech_s1, draft_tech_s2, draft_tech_s3, draft_tech_s4),
    "sales": (draft_sales_s1, draft_sales_s2, draft_sales_s3, draft_sales_s4),
    "user": (draft_user_s1, draft_user_s2, draft_user_s3, draft_user_s4),
}
