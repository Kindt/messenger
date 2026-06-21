"""Consolidated Korus gap registry — «До уверенного 5+» for PM deck section."""

from __future__ import annotations

from html import escape

from scripts.presentation import product_status as ps
from scripts.presentation.data_loader import load_competitors
from scripts.presentation.petal_radar import LEVEL_TO_SCORE, TAB_PETAL_CRITERIA, feature_text_to_score
from scripts.presentation.petal_scoring import (
    KORUS_GAP_TO_5,
    KORUS_USER_GAP,
    explain_criteria_score,
    explain_user_score,
)
from scripts.presentation.user_features import USER_FEATURE_GROUPS

WORK_ENGINEERING = "engineering"
WORK_ENG_OPS = "eng+ops"
WORK_OPS = "ops"
WORK_PRODUCT = "product"
WORK_OUT = "out"

WORK_LABEL = {
    WORK_ENGINEERING: "Доработка продукта",
    WORK_ENG_OPS: "Продукт + приёмка",
    WORK_OPS: "Приёмка на контуре заказчика",
    WORK_PRODUCT: "Продукт / коммерция",
    WORK_OUT: "Вне поставки",
}


def _classify_work(cid: str, gap: str) -> str:
    if cid in ("mobile", "desktop"):
        return WORK_OUT
    if cid in ("fstec", "pricing"):
        return WORK_PRODUCT
    g = gap.lower()
    ops_hints = (
        "ops",
        "stage",
        "prom",
        "sign-off",
        "иб",
        "turn",
        "vapid",
        "idp",
        "live idp",
        "контур",
        "заказчик",
        "фстэк",
        "реестр",
        "soak",
        "lso",
        "сентября",
        "sep 2026",
        "кп",
        "прайс",
        "sla",
        "legal strict",
        "prod import",
        "prod token",
        "live creds",
    )
    eng_ops_ids = {
        "e2ee",
        "vks",
        "sso",
        "search",
        "bots",
        "superapp",
        "multitenant",
        "federation",
        "push",
        "tls",
        "calls",
        "live",
    }
    if any(h in g for h in ops_hints):
        if cid in eng_ops_ids:
            return WORK_ENG_OPS
        return WORK_OPS
    return WORK_ENGINEERING


def _proposal(cid: str, work: str, domain: str) -> str:
    p = {
        "onprem": "Провести нагрузочную проверку и эксплуатационную приёмку на контуре заказчика",
        "export": "Согласовать строгий чеклист экспорта и процедуру комплаенса",
        "audit": "Подготовить пакет для ИБ и регламент аудита на контуре заказчика",
        "fstec": "Запустить продуктовый и юридический процесс экспертизы и включения в реестр",
        "retention": "Согласовать политики хранения с юристами и ИБ заказчика",
        "multitenant": "Подтвердить сценарии нескольких организаций на рабочем контуре",
        "sizing": "Провести формальную нагрузочную проверку на стенде заказчика",
        "pricing": "Публичные якоря стоимости владения / типовой прайс-лист",
        "sla": "Подписать SLA и подтвердить отказоустойчивость на рабочем контуре",
        "superapp": "Использовать каталог интеграций и встроенную панель в пилоте; отдельно развивать богатые мини-формы уровня SmartApps",
        "federation": "Подтвердить сценарий доверенных организаций и межорганизационной переписки на контуре заказчика",
        "vks": "Подтвердить работу конференций, записи и сетевых правил на контуре заказчика",
        "ops": "Подготовить матрицу нагрузок и эксплуатационные регламенты",
        "e2ee": "Провести приёмку ИБ перед массовым включением шифрования",
        "search": "Подтвердить поисковый профиль для большого контура",
        "sso": "Провести проверку входа на реальной системе заказчика",
        "bots": "Довести каталог интеграций и SLA уведомлений от внешних систем до понятного пилотного сценария",
        "mobile": "Отдельный проект — не в рамки текущей веб-поставки",
        "link_preview": "Подтвердить безопасную обработку внешних ссылок и правила выхода в интернет",
        "smartapps_ui": "Уточнить глубину готовых мини-форм поверх существующего каталога и встроенной панели",
        "migration_import": "Подготовить процедуру пакетного импорта на контуре заказчика",
        "fleet_ops": "Подключить мониторинг и эксплуатационные панели на рабочем контуре",
        "push": "Выпустить боевые ключи уведомлений для стенда заказчика",
        "tls": "Подключить реальные домены, сертификаты и защищённое хранение секретов",
        "gdpr_export": "Согласовать строгий юридический сценарий экспорта",
        "fr_opt_shard": "Подтвердить модель распределения организаций на рабочем контуре",
        "load_test": "Провести нагрузочную проверку на рабочем стенде",
        "scim": "Настроить промышленный токен и регламент управления пользователями",
        "directory": "Подключить реальные учётные данные каталога заказчика",
        "live": "Подтвердить массовый сценарий трансляций на стенде заказчика",
        "external_stack": "Проверить выбранный внешний стек вместе с заказчиком и поставщиком; финальное переключение выполнять по согласованной инструкции",
        "chat": "Базовый чат ✓; федерация вынесена в отдельную подключаемую возможность и не считается пробелом базового чата",
        "profile": "Браузер и режим «не беспокоить» ✓; нативные мобильные клиенты — вне текущей поставки",
        "calls": "Подтвердить запись, масштаб конференций и работу в сложных сетях",
        "files_search": "Базовый поиск ✓; DLP-проверки отправки ✓; правила реального вендора — приёмка с ИБ",
        "organize": "Папки и архив ✓; доверенные организации и каталог партнёров ✓; подтвердить холдинговый сценарий на пилоте",
        "integrations": "Каталог интеграций и встроенная панель ✓; довести готовые формы и коннекторы до сценариев заказчика",
        "notify": "Браузерные уведомления ✓; магазинные приложения — вне текущей поставки",
    }
    base = p.get(cid, "Закрыть ограничение и обновить презентацию фактами")
    if work == WORK_OPS:
        return f"{base}; выполняется после появления рабочего контура"
    if work == WORK_ENG_OPS:
        return f"{base}; часть в продукте готовится сейчас, финальная приёмка — на контуре заказчика"
    if work == WORK_PRODUCT:
        return f"{base}; зона продукта и коммерческой политики"
    if work == WORK_OUT:
        return base
    return base


def _collect_rows() -> list[dict[str, str]]:
    data = load_competitors()
    korus = next(p for p in data["products"] if p["id"] == "korus")
    criteria_meta = {c["id"]: c for c in data.get("criteria", [])}

    def crit_title(cid: str) -> str:
        public_titles = {
            "multitenant": "Несколько организаций / шардирование",
            "superapp": "Суперапп и мини-приложения",
            "sso": "Единый вход и каталог сотрудников",
        }
        if cid in public_titles:
            return public_titles[cid]
        c = criteria_meta.get(cid, {})
        return str(c.get("short") or c.get("title") or cid)

    by_cid: dict[str, dict] = {}

    for tab, cids in TAB_PETAL_CRITERIA.items():
        for cid in cids:
            cell = str(korus["features"].get(cid, "—"))
            score = feature_text_to_score(cell)
            _, gap = explain_criteria_score(cid, crit_title(cid), cell, score, "korus")
            gap_text = KORUS_GAP_TO_5.get(cid, gap)
            if cid not in by_cid:
                by_cid[cid] = {
                    "domain": "Radar (критерии ТЗ)",
                    "id": cid,
                    "axis": crit_title(cid),
                    "score": f"{score:.1f}",
                    "gap": gap_text,
                    "where": tab.upper(),
                }
            else:
                prev = by_cid[cid]["where"]
                if tab.upper() not in prev.split(", "):
                    by_cid[cid]["where"] = f"{prev}, {tab.upper()}"

    rows: list[dict[str, str]] = list(by_cid.values())

    for g in USER_FEATURE_GROUPS:
        cell = g.comparisons["korus"]
        score = LEVEL_TO_SCORE.get(cell.level, 3.0)
        _, gap_prom = explain_user_score(g.id, "korus", score)
        gap_user = "; ".join(cell.gaps[:3])
        extra = KORUS_USER_GAP.get(g.id, "")
        gap_full = gap_prom if gap_prom else gap_user
        if extra and extra not in gap_full:
            gap_full = f"{gap_full} {extra}"
        work = _classify_work(g.id, gap_full)
        rows.append(
            {
                "domain": "User (сценарии)",
                "id": g.id,
                "axis": g.title,
                "score": f"{score:.1f}",
                "gap": gap_full,
                "where": "USER",
                "work": work,
                "proposal": _proposal(g.id, work, "user"),
            }
        )

    seen_module: set[str] = set()
    for fid, name, status, note in ps.FEATURES:
        if status != "partial" or fid in seen_module or fid in by_cid:
            continue
        seen_module.add(fid)
        work = _classify_work(fid, note)
        rows.append(
            {
                "domain": "Модуль (partial)",
                "id": fid,
                "axis": name,
                "score": "3.0",
                "gap": note,
                "where": "Block-0",
                "work": work,
                "proposal": _proposal(fid, work, "module"),
            }
        )

    for r in rows:
        if "work" not in r:
            r["work"] = _classify_work(r["id"], r["gap"])
        if "proposal" not in r:
            r["proposal"] = _proposal(r["id"], r["work"], r["domain"])

    rows.sort(key=lambda x: (float(x["score"]), x["domain"], x["axis"]))
    return rows


def _public_text(text: str) -> str:
    replacements = (
        ("Radar (критерии ТЗ)", "Критерии закупки"),
        ("User (сценарии)", "Сценарии сотрудника"),
        ("Модуль (partial)", "Модуль продукта"),
        ("TECH", "IT и ИБ"),
        ("SALES", "Для продаж"),
        ("USER", "Сотруднику"),
        ("PM", "Руководителю"),
        ("Block-0", "Статус продукта"),
        ("LSO-001…007", "отложенная приёмка на контуре заказчика"),
        ("LSO-004", "нагрузочная проверка на контуре заказчика"),
        ("LSO-030/064", "подключение к реальным системам заказчика"),
        ("LSO-040", "массовая проверка трансляций"),
        ("LSO-071", "экспертиза и реестр"),
        ("LSO", "отложенная приёмка"),
        ("wire parity", "техническая совместимость"),
        ("prom OpenMLS", "промышленная криптографическая библиотека"),
        ("prom", "промышленный контур"),
        ("Prom", "Промышленный контур"),
        ("sign-off", "приёмка"),
        ("Sign-off", "Приёмка"),
        ("stage", "стенд заказчика"),
        ("Stage", "Стенд заказчика"),
        ("engineering", "доработка продукта"),
        ("scope web-deck", "рамки текущей веб-поставки"),
        ("charter", "план работ"),
        ("BYO cutover", "подключение внешнего стека заказчика"),
        ("vendor evidence gates", "подтверждение готовности поставщика"),
        ("desired/observed manifests", "описание желаемого и фактического состояния"),
        ("attached probes", "проверки подключения"),
        ("compatibility pack catalog", "каталог совместимости"),
        ("Search SPI", "контракт поискового сервиса"),
        ("cutover reports", "отчёты о переключении"),
        ("admin preflight", "предварительная проверка в админке"),
        ("live BYO cutover", "подключение реального внешнего стека"),
    )
    out = text
    for src, dst in replacements:
        out = out.replace(src, dst)
    return out


def _risk_summary(rows: list[dict[str, str]]) -> str:
    p0 = sum(1 for r in rows if float(r["score"]) < 3.0)
    p1 = sum(1 for r in rows if 3.0 <= float(r["score"]) < 4.5)
    p2 = len(rows) - p0 - p1
    return f"""
<div class="risk-summary">
  <article><strong>{p0}</strong><span>критично для решения о пилоте</span></article>
  <article><strong>{p1}</strong><span>нужно уточнить перед внедрением</span></article>
  <article><strong>{p2}</strong><span>можно вести как план развития</span></article>
</div>
"""


def render_gaps_registry_html() -> str:
    rows = _collect_rows()
    body = []
    for r in rows:
        work = r["work"]
        score_f = float(r["score"])
        prio = "P0" if score_f < 3.0 else ("P1" if score_f < 4.5 else "P2")
        body.append(
            "<tr>"
            f"<td>{escape(prio)}</td>"
            f"<td>{escape(_public_text(r['domain']))}</td>"
            f"<td>{escape(r['axis'])}</td>"
            f"<td>{escape(r['score'])}</td>"
            f"<td>{escape(_public_text(r['where']))}</td>"
            f"<td>{escape(_public_text(r['gap']))}</td>"
            f"<td>{escape(WORK_LABEL.get(work, work))}</td>"
            f"<td>{escape(_public_text(r['proposal']))}</td>"
            "</tr>"
        )
    return f"""
<details class="gaps-registry" id="pm-gaps-registry">
  <summary>Подробный реестр оговорок и проверок ({len(rows)} строк)</summary>
  <p class="petal-rationale-lead">Это служебная таблица для аналитика и владельца пилота: что уже покрыто с оговоркой, что нужно проверить на контуре заказчика и что остаётся вне текущей поставки. Основные выводы уже даны выше, поэтому реестр по умолчанию скрыт.</p>
  {_risk_summary(rows)}
  <div class="table-wrap matrix-focus-wide">
    <table class="matrix-table gaps-table">
      <thead><tr>
        <th scope="col">Уровень</th>
        <th scope="col">Слой</th>
        <th scope="col">Ось / модуль</th>
        <th scope="col">Балл</th>
        <th scope="col">Где в презентации</th>
        <th scope="col">Оговорка / что подтвердить</th>
        <th scope="col">Контур</th>
        <th scope="col">Что делать</th>
      </tr></thead>
      <tbody>{''.join(body)}</tbody>
    </table>
  </div>
</details>
"""
