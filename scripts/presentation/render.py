"""Assemble self-contained HTML deck — 4 tabs, Block 0, calculators."""

from __future__ import annotations

import json
from datetime import date
from html import escape
from typing import Any

from scripts.presentation import calc_ui as cui
from scripts.presentation import capabilities as cap
from scripts.presentation import support_model as sm
from scripts.presentation import content as cnt
from scripts.presentation import marketing as mkt
from scripts.presentation import product_status as ps
from scripts.presentation import sizing_engine as se
from scripts.presentation import module_sizing as ms
from scripts.presentation import sizing_pricing as sp
from scripts.presentation import stacks as stk
from scripts.presentation import visuals as viz
from scripts.presentation import anchors as anc
from scripts.presentation.compare_engine import build_all_rows, render_headroom_badge
from scripts.presentation.calculators import sales_tco, support_cost, tech_capacity
from scripts.presentation.data_loader import load_competitors, load_offerings


def _units_legend() -> str:
    fte = f"{sp.FTE_RATE_RUB_PER_MONTH:,}".replace(",", " ")
    provs = ", ".join(p.label for p in sp.PROVIDERS)
    return f"""
<div class="units-box" id="units-legend">
  <h4>Единицы измерения</h4>
  <dl class="units-dl">
    <dt>RU</dt><dd>зарегистрированные пользователи (учётные записи), не одновременные сессии</dd>
    <dt>₽/год · ₽/мес</dt><dd>рубли РФ, без НДС; infra — серверы и канал, не лицензия ПО конкурента</dd>
    <dt>Infra Korus</dt><dd>медиана смет по {escape(provs)} на дату {sp.PRICE_AS_OF}</dd>
    <dt>FTE</dt><dd>сопровождение (люди), не CPU; ставка {fte} ₽/мес за 1 FTE (8×5)</dd>
    <dt>RAM</dt><dd>суммарная RAM по {anc.link(anc.TECH_SIZING, "методике sizing")}, ГБ; VM-тир — ближайший стандартный объём</dd>
  </dl>
</div>"""


def _price_sources_html() -> str:
    links = "".join(
        f'<li><strong>{escape(p.label)}:</strong> '
        f'<a href="{escape(p.pricing_url)}" rel="noopener noreferrer">{escape(p.pricing_url)}</a> '
        f"— {escape(p.source_note)}</li>"
        for p in sp.PROVIDERS
    )
    return f'<div class="method-box" id="price-sources"><h4>Источники цен infra</h4><ul class="bullet-clean">{links}</ul></div>'


def _fmt_rub(n: int | None) -> str:
    if n is None:
        return "—"
    return sp.fmt_rub(n)


def render_block0() -> str:
    blockers = "".join(f"<li>{escape(b)}</li>" for b in ps.PRODUCTION_BLOCKERS)
    done = len(ps.features_by_status("done"))
    partial = len(ps.features_by_status("partial"))
    planned = len(ps.features_by_status("planned"))
    out_n = len(ps.features_by_status("out"))
    donut = mkt.wrap_figure(viz.render_feature_donut_svg(), "Распределение функций по статусу")

    def _feature_table(status: str) -> str:
        rows = ps.features_by_status(status)
        if not rows:
            return ""
        body = "".join(
            f"<tr><td>{escape(f[1])}</td><td>{ps.tag_html(f[2])}</td>"
            f"<td>{escape(f[3]) if f[3] else '—'}</td></tr>"
            for f in rows
        )
        return f"<table class='feature-table'><tr><th>Модуль</th><th>Статус</th><th>Примечание</th></tr>{body}</table>"

    return f"""
<section id="block-0" class="block-0 hero-warning" aria-label="Статус продукта">
  <div class="hero-inner">
    <p class="deck-kicker">Korus Messenger · AvandocMsg</p>
    <h1>Корпоративный мессенджер</h1>
    <p class="stage-badge">{escape(ps.PRODUCT_STAGE_LABEL)}</p>
    <p class="disclaimer">Продукт <strong>не готов</strong> к промышленной эксплуатации. Демонстрация возможностей на <strong>лабораторном dev-стенде</strong>.</p>
    <div class="stat-row">
      <span class="stat-pill">Автотесты UI: {ps.PLAYWRIGHT_PASSED}/{ps.PLAYWRIGHT_TOTAL}</span>
      <span class="stat-pill">Реализовано: {done}</span>
      <span class="stat-pill">Частично: {partial}</span>
      <span class="stat-pill">Версия {ps.PRODUCT_VERSION}</span>
    </div>
    <div class="block0-grid">
      <div>{donut}</div>
      <div class="callout callout-warn">
        <h2 class="block0-h2">Что мешает production</h2>
        <ul class="bullet-clean">{blockers}</ul>
      </div>
    </div>
    <details class="feature-details">
      <summary>Полная матрица модулей по статусу</summary>
      <p class="small">Карточки «что умеет приложение» — в {anc.link(anc.PM_CAPABILITIES, "разделе «Что умеет продукт»")}.</p>
      <h3 class="detail-h">Реализовано ({done})</h3>
      {_feature_table("done")}
      <h3 class="detail-h">Частично ({partial})</h3>
      {_feature_table("partial")}
      <h3 class="detail-h">Запланировано и вне scope</h3>
      {_feature_table("planned")}
      {_feature_table("out")}
    </details>
  </div>
</section>
"""


def render_competitor_list() -> str:
    data = load_competitors()
    rows = []
    offerings = load_offerings()
    for p in data["products"]:
        n = sum(1 for o in offerings if o["product_id"] == p["id"])
        rows.append(
            f"<tr><td>{escape(p['label'])}</td><td>{escape(p['tier'])}</td>"
            f"<td>{escape(p['deployment'])}</td><td>{n}</td></tr>"
        )
    return (
        "<div class='table-wrap'><table class='compare-table'><thead><tr><th>Продукт</th><th>Tier</th>"
        f"<th>Deploy</th><th>Offerings</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div>"
    )


def render_compare_table(limit: int | None = None) -> str:
    rows_html = []
    all_rows = build_all_rows(load_offerings())
    slice_rows = all_rows[:limit] if limit else all_rows
    prov_headers = "".join(
        f'<th scope="col" class="prov-col" title="{escape(p.pricing_url)}">'
        f"{escape(p.label)},<br/>₽/год</th>"
        for p in se.PROVIDERS
    )
    for row in slice_rows:
        o = row.offering
        if not o.get("price_is_public", True):
            comp_cell = "нет публичного прайса"
        else:
            comp_cell = _fmt_rub(row.competitor_total_yearly_rub)
        badge = render_headroom_badge(row)
        src = escape(o["source_url"])
        yearly_vals = [p.yearly_rub for p in row.korus_providers]
        min_y = min(yearly_vals) if yearly_vals else 0
        prov_cells = ""
        for p in row.korus_providers:
            cls = ' class="price-min"' if p.yearly_rub == min_y and len(yearly_vals) > 1 else ""
            prov_cells += f"<td{cls}>{_fmt_rub(p.yearly_rub)}</td>"
        rows_html.append(
            f"<tr><td>{escape(o['label'])} <span class='row-product'>({escape(o['product_id'])})</span></td>"
            f"<td>{o['value']:,}</td>".replace(",", " ")
            + f"<td>{comp_cell}</td>"
            f"{prov_cells}"
            f"<td class='med-col'>{_fmt_rub(row.korus_infra_yearly_rub)} {badge}</td>"
            f'<td class="src-cell"><a href="{src}" rel="noopener noreferrer">{src[:32]}…</a></td></tr>'
        )
    more = ""
    if limit and len(all_rows) > limit:
        more = f'<p class="table-more"><a href="#sales-s3">Ещё {len(all_rows) - limit} строк → вкладка «Продажная»</a></p>'
    return (
        "<div class='table-wrap table-wrap-wide'><table class='compare-table compare-tco compare-tco-wide'><thead><tr>"
        "<th scope='col'>Тариф конкурента</th>"
        "<th scope='col' title='Зарегистрированные пользователи'>RU,<br/>рег.</th>"
        "<th scope='col' title='Годовая стоимость по публичному прайсу'>Конкурент,<br/>₽/год</th>"
        f"{prov_headers}"
        "<th scope='col' title='Медиана трёх провайдеров'>Медиана,<br/>₽/год</th>"
        "<th scope='col'>Источник</th></tr></thead><tbody>"
        f"{''.join(rows_html)}</tbody></table></div>"
        "<p class='footnote'>* Зелёная ячейка — минимальная смета infra среди провайдеров; медиана — для диаграммы TCO; "
        "headroom — запас RU в том же VM-тире</p>"
        f"{more}"
    )


def render_feature_matrix() -> str:
    data = load_competitors()
    crit = data["criteria"]
    header = "".join(
        f"<th scope='col' title='{escape(c['title'])}'>{escape(c['title'][:18])}</th>"
        for c in crit
    )
    body = []
    for p in data["products"]:
        cells = "".join(f"<td>{escape(p['features'].get(c['id'], '—'))}</td>" for c in crit)
        body.append(f"<tr><th scope='row'>{escape(p['label'])}</th>{cells}</tr>")
    return (
        "<p class='small'>Полная матрица 18×11 — для архитекторов. Заголовки сокращены; полный текст — при наведении.</p>"
        f"<div class='matrix-scroll'><table class='matrix-table matrix-full'>"
        f"<thead><tr><th scope='col'>Продукт</th>{header}</tr></thead>"
        f"<tbody>{''.join(body)}</tbody></table></div>"
    )


def _section(tab: str, idx: int, title: str, body: str, extra: str = "") -> str:
    sid = f"{tab}-s{idx}"
    wrapped = mkt.wrap_section(body, f"{tab}-s{idx}", title)
    return f'<article id="{sid}" class="subsection">{wrapped}{extra}</article>'


def _sub_nav(tab: str) -> str:
    titles = cnt.SECTION_TITLES[tab]
    links = "".join(
        f'<a href="#{tab}-s{i}">{escape(t)}</a>' for i, t in enumerate(titles, start=1)
    )
    return f'<nav class="sub-nav" aria-label="Подразделы">{links}</nav>'


def _tab_shell(tab: str, inner: str) -> str:
    intro = escape(cnt.TAB_INTRO[tab])
    return f'<p class="tab-intro">{intro}</p>{_sub_nav(tab)}{inner}'


def render_tab_pm() -> str:
    data = load_competitors()
    tco_chart = viz.render_tco_chart_html(build_all_rows(load_offerings()), limit=5)
    t = cnt.SECTION_TITLES["pm"]
    s1 = _section(
        "pm",
        1,
        t[0],
        cnt.draft_pm_s1() + cap.render_capability_cards(),
        mkt.wrap_figure(viz.render_roadmap_svg(), "Дорожная карта"),
    )
    s2 = _section(
        "pm",
        2,
        t[1],
        cnt.draft_pm_s2() + render_competitor_list(),
        cap.render_focus_matrix(data["products"]),
    )
    s3 = _section(
        "pm",
        3,
        t[2],
        cnt.draft_pm_s3() + _units_legend(),
        render_compare_table(limit=6) + tco_chart,
    )
    s4 = _section("pm", 4, t[3], cnt.draft_pm_s4(), _calc_pm_html())
    return _tab_shell("pm", s1 + s2 + s3 + s4)


def render_tab_tech() -> str:
    rows = build_all_rows(load_offerings())
    ru = rows[0].korus_at_competitor_ru if rows else 10_000
    t = cnt.SECTION_TITLES["tech"]
    s1 = _section(
        "tech",
        1,
        t[0],
        cnt.draft_tech_s1(),
        mkt.wrap_figure(viz.render_architecture_svg(), "Логическая схема прототипа")
        + stk.render_node_stack_table()
        + stk.render_competencies_table(),
    )
    s2 = _section(
        "tech",
        2,
        t[1],
        cnt.draft_tech_s2(),
        render_competitor_list() + stk.render_competitor_stacks_table(),
    )
    s3 = _section(
        "tech",
        3,
        t[2],
        cnt.draft_tech_s3(),
        mkt.wrap_figure(viz.render_ram_bar_svg(ru), "RAM prod full")
        + stk.render_plugin_platform()
        + render_feature_matrix(),
    )
    s4 = _section("tech", 4, t[3], cnt.draft_tech_s4(), _calc_tech_html())
    return _tab_shell("tech", s1 + s2 + s3 + s4)


def render_tab_sales() -> str:
    compare = render_compare_table()
    tco_chart = viz.render_tco_chart_html(build_all_rows(load_offerings()), limit=8)
    t = cnt.SECTION_TITLES["sales"]
    s1 = _section("sales", 1, t[0], cnt.draft_sales_s1())
    s2 = _section("sales", 2, t[1], cnt.draft_sales_s2(), render_competitor_list())
    s3 = _section(
        "sales",
        3,
        t[2],
        cnt.draft_sales_s3() + _units_legend() + _price_sources_html(),
        compare + tco_chart,
    )
    s4 = _section("sales", 4, t[3], cnt.draft_sales_s4(), _calc_sales_html())
    return _tab_shell("sales", s1 + s2 + s3 + s4)


def render_tab_user() -> str:
    t = cnt.SECTION_TITLES["user"]
    s1 = _section(
        "user",
        1,
        t[0],
        cnt.draft_user_s1(),
        mkt.wrap_figure(viz.render_user_timeline_svg(), "Типичный рабочий день"),
    )
    s2 = _section("user", 2, t[1], cnt.draft_user_s2())
    s3 = _section("user", 3, t[2], cnt.draft_user_s3())
    s4 = _section("user", 4, t[3], cnt.draft_user_s4(), _user_wizard_html())
    return _tab_shell("user", s1 + s2 + s3 + s4)


def _calc_sales_html() -> str:
    form = (
        cui.field_number("calc-sales-ru", "Зарегистрированные пользователи (RU)", 7500)
    )
    return (
        '<p class="calc-intro">Infra по публичным прайсам '
        '<a href="#price-sources">REG.RU · Yandex Cloud · Timeweb</a>. '
        "Без лицензии, TURN, НДС.</p>"
        + cui.calc_shell(
            "calc-sales",
            "Калькулятор infra",
            "Смета серверов и канала на ваше число рег.",
            form,
            "calc-sales-out",
            "calc-sales-run",
            accent="indigo",
        )
    )


def _calc_tech_module_table() -> str:
    rows = []
    for m in ms.PRODUCTION_MODULES:
        req_attr = f' data-requires="{",".join(m.requires)}"' if m.requires else ""
        if m.required:
            check_cell = (
                f"<input type='checkbox' class='calc-mod calc-mod-locked' data-mod='{escape(m.id)}'"
                f" checked disabled title='Обязательное ядро prod full'/>"
            )
        else:
            checked = " checked" if m.default_enabled else ""
            check_cell = (
                f"<input type='checkbox' class='calc-mod calc-mod-opt' data-mod='{escape(m.id)}'"
                f"{checked}{req_attr}/>"
            )
        mode_hint = {"active": "нагрузка+", "passive": "зеркало", "cluster": "кворум"}.get(m.replica_mode, "")
        if m.max_replicas > 1 and not m.per_plugin:
            rep_cell = (
                f'<input type="number" class="calc-mod-rep" data-mod="{escape(m.id)}"'
                f' data-max="{m.max_replicas}" min="1" max="{m.max_replicas}" value="1"'
                f' title="{mode_hint}"/>'
            )
        elif m.per_plugin:
            rep_cell = "×плагины"
        else:
            rep_cell = "1"
        rows.append(
            f"<tr><td>{check_cell}</td>"
            f"<td>{escape(m.label)}</td><td>{m.ram_gb} ГБ</td><td>{m.vcpu} vCPU</td><td>{rep_cell}</td>"
            f"<td class='muted'>{mode_hint}</td></tr>"
        )
    return (
        '<p class="small calc-mod-hint"><strong>Ядро</strong> (серые галки) — без этого prod не стартует. '
        "<strong>Workers</strong> — отдельные строки по compose-профилям (retention, export, push…). "
        "<strong>LiveKit</strong> — SFU для групповых звонков (mesh работает без него). "
        "Зависимости: indexer→Solr, archiver→archive PG, ZooKeeper→Solr.</p>"
        '<div class="table-wrap calc-mod-table-wrap"><table class="feature-table calc-mod-table">'
        "<thead><tr><th></th><th>Модуль</th><th>RAM/экз.</th><th>vCPU/экз.</th><th>Экз.</th><th>Реплика</th></tr></thead>"
        f"<tbody>{''.join(rows)}</tbody></table></div>"
    )


def _calc_tech_html() -> str:
    cap_form = (
        '<div class="calc-section-label">Модули и реплики</div>'
        + _calc_tech_module_table()
        + '<div class="calc-section-label">Хранилище и бэкап</div>'
        + cui.field_number("calc-tech-mod-ssd", "SSD, ТБ (без бэка)", 2, min_val=0, step="0.1")
        + cui.field_number("calc-tech-mod-hdd", "HDD archive, ТБ", 5, min_val=0, step="0.1")
        + cui.field_number("calc-tech-mod-backup-ram", "Доп. RAM бэкап/DR, ГБ", 0, min_val=0)
        + cui.field_number(
            "calc-tech-mod-backup-disk",
            "Коэф. диска (снапшоты/DR)",
            1.0,
            min_val=1,
            step="0.05",
        )
        + cui.field_number(
            "calc-tech-mod-backup-ops",
            "Коэф. ops (мониторинг/бэкап)",
            1.0,
            min_val=0.5,
            step="0.1",
        )
        + '<p class="small calc-backup-presets">Пресеты: '
        '<button type="button" class="calc-preset" data-ram="0" data-disk="1" data-ops="1">нет</button> '
        '<button type="button" class="calc-preset" data-ram="2" data-disk="1.35" data-ops="1.4">standard</button> '
        '<button type="button" class="calc-preset" data-ram="8" data-disk="2" data-ops="2">DR</button></p>'
        + cui.field_checkbox("calc-tech-mod-ha", "HA: зеркала PG/Redis/NATS + ≥2 app", False)
        + cui.field_number("calc-tech-mod-plugins", "Плагины integrations", 0, min_val=0)
        + cui.field_number(
            "calc-tech-cap-quote-ru",
            "RU для сметы (0 = макс. по модулям)",
            0,
            min_val=0,
            placeholder="авто",
        )
    )
    res_form = (
        '<div class="calc-section-label">Пользователи и нагрузка</div>'
        + cui.field_number("calc-tech-ru", "Всего рег. пользователей (RU)", 10_000)
        + cui.field_number(
            "calc-tech-peak-online",
            "Пик онлайн (0 = авто из модели нагрузки)",
            0,
            min_val=0,
            placeholder="авто",
        )
        + cui.field_number(
            "calc-tech-peak-msg",
            "Пик msg/s (0 = авто)",
            0,
            min_val=0,
            step="0.1",
            placeholder="авто",
        )
        + cui.field_number("calc-tech-msgs-day", "Сообщений / DAU / день", 40, min_val=1)
        + '<div class="calc-section-label">Хранение</div>'
        + cui.field_number(
            "calc-tech-gb-user",
            "Файлы, ГБ / рег. / год",
            0.5,
            min_val=0,
            step="0.1",
        )
        + cui.field_number("calc-tech-retention", "Retention, лет", 3, min_val=1)
        + '<p class="small">Состав модулей, реплики и бэкап — в калькуляторе слева.</p>'
    )
    return (
        '<p class="calc-intro">Production full stack. Dev-min (QEMU) — только разработка, не sizing.</p>'
        + '<div class="calc-dual">'
        + cui.calc_shell(
            "calc-tech-cap",
            "Сколько пользователей выдержит",
            "Модули, экземпляры, бэкап → предел RU и смета infra на выбранном составе.",
            cap_form,
            "calc-tech-cap-out",
            "calc-tech-cap-run",
            accent="emerald",
        )
        + cui.calc_shell(
            "calc-tech-res",
            "Какие ресурсы нужны",
            "Задайте нагрузку → таблица модулей; справа — смета infra.",
            res_form,
            "calc-tech-res-out",
            "calc-tech-res-run",
            accent="sky",
        )
        + "</div>"
    )


def _calc_pm_html() -> str:
    form = (
        '<div class="calc-section-label">Масштаб и режим</div>'
        + cui.field_number("calc-pm-ru", "Зарегистрированные пользователи (RU)", 12000)
        + cui.field_select(
            "calc-pm-sla",
            "Режим поддержки",
            [("business", "Будни 8×5"), ("24x7", "Круглосуточно 24×7")],
        )
        + cui.field_checkbox("calc-pm-upd", "Регулярные обновления релизов", True)
        + cui.field_select(
            "calc-pm-team",
            "Команда",
            [("shared", "Общий service desk"), ("dedicated", "Выделенная на мессенджер")],
        )
        + '<div class="calc-section-label">Инфраструктура и интеграции</div>'
        + cui.field_select(
            "calc-pm-topo",
            "Топология",
            [("compact", "Компактный контур"), ("cluster", "Кластер (несколько узлов)")],
        )
        + cui.field_select(
            "calc-pm-integ",
            "Интеграции",
            [("none", "Нет"), ("few", "1–3 (LDAP, боты…)"), ("many", "4+ / кастом")],
            selected="few",
        )
        + cui.field_select(
            "calc-pm-orgs",
            "Организации / филиалы",
            [("one", "1"), ("few", "2–5"), ("many", "6+")],
        )
        + '<div class="calc-section-label">Пользователи и безопасность</div>'
        + cui.field_select(
            "calc-pm-l1",
            "L1 (первая линия у пользователей)",
            [("none", "Нет"), ("partial", "Частично"), ("full", "Полная L1")],
            selected="partial",
        )
        + cui.field_select(
            "calc-pm-training",
            "Обучение",
            [("none", "Не нужно"), ("annual", "Ежегодно"), ("quarterly", "Ежеквартально")],
            selected="annual",
        )
        + cui.field_select(
            "calc-pm-e2ee",
            "E2EE / MLS",
            [("off", "Не используется"), ("roadmap", "В приёмке"), ("prod", "В промышленной эксплуатации")],
            selected="roadmap",
        )
        + cui.field_select(
            "calc-pm-compliance",
            "ИБ / комплаенс",
            [("none", "Базовый"), ("dlp", "DLP / расширенный аудит"), ("fstec", "Требования ФСТЭК")],
        )
        + cui.field_select(
            "calc-pm-dr",
            "Резерв / DR",
            [("none", "Нет"), ("backup", "Резервный ЦОД"), ("full", "DR + регулярные учения")],
        )
        + '<div class="calc-section-label">Экономика команды</div>'
        + cui.field_select(
            "calc-pm-staffing",
            "Штат / субподряд",
            [("inhouse", "Штат заказчика"), ("outsource", "Аутстафф / интегратор")],
        )
        + cui.field_select(
            "calc-pm-region",
            "Регион ставок",
            [("msk", "Москва / СПб"), ("region", "Регионы РФ")],
        )
        + cui.field_checkbox("calc-pm-overhead", "Надбавка 5% (НДС, командировки)", True)
    )
    return (
        '<p class="calc-intro">Полная модель сопровождения (FTE × ставка). '
        'Infra — <a href="#tech-s4">калькулятор infra</a>.</p>'
        + cui.calc_shell(
            "calc-pm",
            "Калькулятор сопровождения",
            "Все факторы: от RU до DR и ФСТЭК.",
            form,
            "calc-pm-out",
            "calc-pm-run",
            accent="emerald",
        )
    )


def _user_wizard_html() -> str:
    return """
<div class="user-wizard calc-card">
  <h4 class="wizard-h">Мастер сценариев</h4>
  <select id="wizard-scenario" class="wizard-select">
    <option value="message">Написать коллеге</option>
    <option value="search">Найти файл</option>
    <option value="call">Позвонить</option>
  </select>
  <ol id="wizard-steps" class="wizard-steps"></ol>
  <h4 class="wizard-h">Частые вопросы</h4>
  <details class="faq"><summary>Чем не Telegram?</summary><p>Корпоративный аудит, хранение по политике компании, выгрузка для комплаенса.</p></details>
  <details class="faq"><summary>Нужно ли ставить приложение?</summary><p>Достаточно браузера; можно добавить ярлык на рабочий стол.</p></details>
  <h4 class="wizard-h">Краткий тур</h4>
  <ol class="tour-steps"><li>Вход</li><li>Список чатов</li><li>Сообщение</li><li>Поиск</li></ol>
</div>"""


def deck_data_json() -> dict[str, Any]:
    providers = [
        {
            "id": p.id,
            "label": p.label,
            "pricing_url": p.pricing_url,
            "ram_rub_gb": p.rub_per_gb_ram_month,
            "vcpu_rub": p.rub_per_vcpu_month,
            "ssd_tb_rub": p.rub_per_tb_ssd_month,
            "hdd_tb_rub": p.rub_per_tb_hdd_month,
            "channel_200": p.rub_channel_200_mbps,
            "channel_1g": p.rub_channel_1gbps,
            "ops_base": p.rub_ops_base_month,
        }
        for p in se.PROVIDERS
    ]
    offerings = [
        {
            "id": o["id"],
            "label": o["label"],
            "value": o["value"],
            "product_id": o["product_id"],
            "price_is_public": o.get("price_is_public", True),
        }
        for o in load_offerings()
    ]
    max_as_of = max(o["source_accessed_at"] for o in load_offerings())
    return {
        "ram_anchors": list(se.RAM_ANCHORS),
        "vm_tiers": list(se.VM_RAM_TIERS),
        "providers": providers,
        "offerings": offerings,
        "price_as_of": se.PRICE_AS_OF,
        "offerings_max_as_of": max_as_of,
        "fte_rate_monthly": se.FTE_RATE_RUB_PER_MONTH,
        "support": sm.support_model_json(),
        "modules": ms.modules_catalog_json(),
        "load_defaults": {
            "msgs_per_user_day": ms.DEFAULT_MSGS_PER_USER_DAY,
            "gb_files_per_user_yr": ms.DEFAULT_GB_FILES_PER_USER_YR,
            "retention_years": ms.DEFAULT_RETENTION_YEARS,
            "peak_burst": ms.PEAK_MSG_BURST,
        },
        "backup_profiles": {
            k: {"disk_mult": v[0], "ram_gb": v[1], "ops_mult": v[2], "label": v[3]}
            for k, v in ms.BACKUP_PROFILES.items()
        },
        "scenarios": {
            "message": ["Откройте чат", "Выберите коллегу", "Напишите сообщение"],
            "search": ["Введите запрос", "Выберите результат", "Откройте файл"],
            "call": ["Откройте чат", "Нажмите звонок", "Разрешите микрофон"],
        },
    }


def _deck_css() -> str:
    return """
:root {
  --brand: #1e3a5f; --accent: #6366f1; --accent-soft: #eef2ff;
  --ok: #047857; --warn: #c2410c; --bg: #f8fafc; --surface: #fff;
  --text: #111827; --muted: #6b7280; --border: #e5e7eb; --radius: 10px;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
  margin: 0; background: var(--bg); color: var(--text); line-height: 1.55;
  font-size: 15px;
}
.hero-inner, .tab-panel, .deck-footer-inner { max-width: 980px; margin: 0 auto; padding: 0 20px; }
.block-0 {
  background: linear-gradient(180deg, #fff7ed 0%, #fff 100%);
  border-bottom: 3px solid #fdba74; padding: 28px 0 24px;
}
.deck-kicker { font-size: 13px; color: var(--muted); margin: 0 0 4px; letter-spacing: .02em; }
.block-0 h1 { font-size: 28px; margin: 0 0 8px; color: var(--brand); font-weight: 700; }
.stage-badge {
  display: inline-block; background: #fef3c7; color: #92400e; border: 1px solid #fcd34d;
  padding: 4px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; margin-bottom: 12px;
}
.disclaimer { max-width: 720px; margin: 0 0 16px; color: #374151; }
.stat-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.stat-pill {
  background: var(--surface); border: 1px solid var(--border); border-radius: 999px;
  padding: 6px 14px; font-size: 13px; color: #374151;
}
.block0-grid { display: grid; grid-template-columns: minmax(280px, 420px) 1fr; gap: 20px; align-items: start; }
.block0-h2 { font-size: 16px; margin: 0 0 8px; color: var(--brand); }
.feature-details {
  margin-top: 20px; background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 12px 16px;
}
.feature-details summary { cursor: pointer; font-weight: 600; color: var(--brand); }
.detail-h { font-size: 14px; color: #374151; margin: 16px 0 8px; }
.tab-bar {
  display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 20px; position: sticky; top: 0;
  background: rgba(248,250,252,.95); backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border); z-index: 20; justify-content: center;
}
.tab-bar button {
  min-height: 44px; padding: 8px 18px; border: 1px solid #c7d2fe; background: var(--surface);
  cursor: pointer; border-radius: 999px; font-size: 14px; font-weight: 500; color: #4338ca;
  transition: background .15s, color .15s;
}
.tab-bar button:hover { background: var(--accent-soft); }
.tab-bar button[aria-selected="true"] {
  background: var(--accent); color: #fff; border-color: var(--accent);
  box-shadow: 0 2px 8px rgba(99,102,241,.35);
}
.tab-panel { display: none; padding: 24px 0 40px; }
.tab-panel.active { display: block; }
.tab-intro {
  background: var(--accent-soft); border-left: 4px solid var(--accent);
  padding: 12px 16px; border-radius: 0 var(--radius) var(--radius) 0;
  margin: 0 20px 16px; max-width: 940px; color: #3730a3; font-size: 14px;
}
.sub-nav {
  display: flex; flex-wrap: wrap; gap: 8px; margin: 0 20px 24px; max-width: 940px;
}
.sub-nav a {
  font-size: 13px; text-decoration: none; color: #4338ca; background: var(--surface);
  border: 1px solid #c7d2fe; padding: 6px 12px; border-radius: 999px;
}
.sub-nav a:hover { background: var(--accent-soft); }
.subsection {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 20px 22px; margin: 0 20px 20px; max-width: 940px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.section-headline {
  font-size: 18px; margin: 0 0 12px; color: var(--brand);
  border-bottom: 2px solid var(--accent); padding-bottom: 8px;
}
.section-body p { margin: 0 0 10px; }
.bullet-clean { margin: 8px 0; padding-left: 20px; }
.bullet-clean li { margin: 4px 0; }
.status-link a { color: #4338ca; font-size: 13px; }
.table-wrap { overflow-x: auto; margin: 12px 0; border-radius: 8px; border: 1px solid var(--border); }
.compare-table, .feature-table, .matrix-table {
  width: 100%; border-collapse: collapse; font-size: 13px; background: var(--surface);
}
.compare-table thead th, .feature-table th, .matrix-table th {
  background: var(--accent-soft); text-align: left; font-weight: 600; color: var(--brand);
  padding: 10px 12px; border-bottom: 2px solid #c7d2fe;
}
.compare-table td, .feature-table td, .matrix-table td {
  padding: 8px 12px; border-bottom: 1px solid var(--border); vertical-align: top;
}
.compare-table tbody tr:nth-child(even), .feature-table tbody tr:nth-child(even) { background: #f9fafb; }
.row-product { color: var(--muted); font-size: 11px; }
.src-cell a { color: #4338ca; word-break: break-all; }
.matrix-scroll { overflow-x: auto; margin: 12px 0; }
.tag { display: inline-block; font-size: 11px; padding: 2px 10px; border-radius: 999px; font-weight: 600; }
.tag-done { background: #dcfce7; color: #166534; }
.tag-partial { background: #fef9c3; color: #854d0e; border: 1px dashed #fbbf24; }
.tag-planned { background: #e0e7ff; color: #3730a3; }
.tag-out { background: #f3f4f6; color: #4b5563; }
.chip-headroom {
  background: #dcfce7; color: #166534; font-size: 11px; padding: 2px 8px;
  border-radius: 999px; margin-left: 4px; white-space: nowrap;
}
.callout { padding: 14px 16px; border-radius: var(--radius); margin: 12px 0; }
.callout-warn { background: #fff7ed; border: 1px solid #fdba74; }
.callout-info { background: #eff6ff; border-left: 4px solid #3b82f6; }
.card-grid { display: grid; gap: 12px; margin: 12px 0; }
.card-grid-3 { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.card {
  background: #f9fafb; border: 1px solid var(--border); border-radius: 8px; padding: 14px;
  border-left: 4px solid var(--accent);
}
.card h4 { margin: 0 0 6px; font-size: 14px; color: var(--brand); }
.card p { margin: 0; font-size: 13px; color: #4b5563; }
.fig { margin: 16px 0; text-align: center; }
.fig svg { max-width: 100%; height: auto; border: 1px solid var(--border); border-radius: 8px; background: #fff; }
.fig-cap { font-size: 12px; color: var(--muted); margin-top: 8px; }
.footnote, .small { font-size: 12px; color: var(--muted); }
.calc-assumptions { margin: 12px 0 16px; padding: 12px 14px; background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; }
.calc-assumptions p { margin: 0 0 6px; }
.calc-assumptions p:last-child { margin-bottom: 0; }
.calc-mod-table-wrap { margin: 8px 0 12px; max-height: 480px; overflow: auto; }
.calc-mod-table { font-size: 12px; }
.calc-mod-table input.calc-mod-rep { width: 52px; padding: 4px 6px; font-size: 12px; border-radius: 6px; border: 1px solid #cbd5e1; }
.calc-backup-presets { margin: 4px 0 10px; }
.calc-backup-presets .calc-preset { margin-right: 6px; padding: 2px 8px; font-size: 11px; border-radius: 6px; border: 1px solid #cbd5e1; background: #f8fafc; cursor: pointer; }
.calc-backup-presets .calc-preset:hover { background: #e2e8f0; }
.calc-mod-table td.muted { font-size: 11px; color: var(--muted); }
.calc-mod-table input.calc-mod-locked { cursor: not-allowed; opacity: 0.55; }
.calc-mod-table tr.calc-mod-dep-off { opacity: 0.45; }
.calc-mod-hint { margin: 0 0 8px; color: var(--muted); }
.calc-module-check { font-size: 13px; display: flex; gap: 6px; align-items: flex-start; }
.stack-block { margin-top: 24px; }
.stack-block h4 { margin: 0 0 8px; font-size: 15px; }
.stack-notes { margin: 8px 0 0 18px; padding: 0; }
.table-more { font-size: 13px; margin-top: 8px; }
.table-more a { color: #4338ca; }
.calc-card {
  background: #f0fdf4; border: 1px solid #86efac; border-radius: var(--radius);
  padding: 16px; margin-top: 12px;
}
.calc-dual {
  display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 12px 0 20px;
  align-items: start;
}
@media (max-width: 1024px) { .calc-dual { grid-template-columns: 1fr; } }
.calc-dual .calc-shell { margin: 0; height: 100%; min-width: 0; }
.calc-res-split {
  display: grid; grid-template-columns: 1fr minmax(0, 280px); gap: 16px; align-items: start;
  margin-top: 12px; min-width: 0;
}
.calc-res-split > * { min-width: 0; }
.calc-res-main { min-width: 0; overflow-x: auto; }
/* В двух колонках смету — под таблицей, иначе rail обрезается */
.calc-dual .calc-res-split { grid-template-columns: 1fr; }
@media (max-width: 720px) { .calc-res-split { grid-template-columns: 1fr; } }
.calc-cost-rail {
  background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%);
  border: 1px solid #86efac; border-radius: 12px; padding: 14px 16px;
  box-sizing: border-box; width: 100%; max-width: 100%;
}
.calc-cost-rail h5 { margin: 0 0 10px; font-size: 13px; color: #065f46; text-transform: uppercase; letter-spacing: .04em; }
.calc-cost-median { font-size: 22px; font-weight: 800; color: #047857; line-height: 1.2; margin-bottom: 12px; }
.calc-cost-median span { display: block; font-size: 11px; font-weight: 600; color: #6b7280; margin-top: 2px; }
.calc-cost-list { list-style: none; margin: 0 0 10px; padding: 0; }
.calc-cost-list li {
  display: flex; justify-content: space-between; gap: 8px; font-size: 13px;
  padding: 6px 0; border-bottom: 1px solid #d1fae5;
  flex-wrap: wrap; word-break: break-word;
}
.calc-cost-list li.is-min strong { color: #047857; }
.calc-cost-list li:last-child { border-bottom: none; }
.calc-user-hero .calc-stat-val { color: #047857; }
.calc-shell {
  border: 1px solid var(--border); border-radius: 14px;
  margin: 12px 0; background: var(--surface);
  box-shadow: 0 4px 14px rgba(30,58,95,.08);
  min-width: 0;
}
.calc-shell-head {
  padding: 18px 22px; color: #fff;
  background: linear-gradient(135deg, #1e3a5f 0%, #4338ca 100%);
  border-radius: 14px 14px 0 0;
}
.calc-shell-emerald .calc-shell-head { background: linear-gradient(135deg, #065f46, #059669); }
.calc-shell-sky .calc-shell-head { background: linear-gradient(135deg, #0369a1, #0ea5e9); }
.calc-shell-indigo .calc-shell-head { background: linear-gradient(135deg, #312e81, #6366f1); }
.calc-shell-title { margin: 0; font-size: 17px; font-weight: 700; }
.calc-shell-sub { margin: 6px 0 0; font-size: 13px; opacity: .92; }
.calc-shell-body { padding: 18px 20px 22px; }
.calc-form-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px 16px;
}
.calc-section-label {
  grid-column: 1 / -1; font-size: 11px; font-weight: 700; text-transform: uppercase;
  letter-spacing: .06em; color: #6366f1; margin-top: 4px; padding-top: 8px;
  border-top: 1px dashed var(--border);
}
.calc-section-label:first-child { border-top: none; padding-top: 0; margin-top: 0; }
.calc-field { display: flex; flex-direction: column; gap: 6px; }
.calc-field label { font-size: 12px; font-weight: 600; color: var(--brand); line-height: 1.35; }
.calc-field input, .calc-field select {
  padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px;
  font-size: 14px; background: #fff; width: 100%;
}
.calc-field input:focus, .calc-field select:focus {
  outline: 2px solid #a5b4fc; border-color: #6366f1;
}
.calc-field-check { justify-content: flex-end; }
.calc-field-check label { font-weight: 500; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.calc-actions { margin-top: 16px; }
.btn-calc {
  background: linear-gradient(135deg, #4338ca, #6366f1); color: #fff; border: none;
  padding: 12px 28px; border-radius: 10px; cursor: pointer; font-weight: 700;
  font-size: 14px; box-shadow: 0 2px 8px rgba(99,102,241,.35);
}
.btn-calc:hover { filter: brightness(1.05); }
.calc-result-panel { margin-top: 18px; min-width: 0; overflow: visible; }
.calc-hero {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px; margin-bottom: 16px;
}
.calc-stat {
  background: linear-gradient(180deg, #f0fdf4, #fff); border: 1px solid #bbf7d0;
  border-radius: 12px; padding: 14px 10px; text-align: center;
}
.calc-stat-val { font-size: 20px; font-weight: 800; color: #047857; line-height: 1.2; }
.calc-stat-label { font-size: 10px; color: var(--muted); margin-top: 4px; text-transform: uppercase; letter-spacing: .04em; }
.calc-provider-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px;
}
.calc-provider-card {
  border: 1px solid var(--border); border-radius: 12px; overflow: hidden; background: #fafafa;
}
.calc-provider-card.is-min { border-color: #86efac; box-shadow: 0 0 0 2px rgba(34,197,94,.2); }
.calc-provider-head {
  padding: 10px 14px; background: var(--accent-soft); font-weight: 700; font-size: 13px;
  display: flex; justify-content: space-between; align-items: center;
}
.calc-provider-head a { font-weight: 500; font-size: 11px; }
.calc-provider-total {
  padding: 12px 14px; font-size: 15px; font-weight: 700; color: var(--brand);
  border-bottom: 1px solid var(--border); background: #fff;
}
.price-min { background: #ecfdf5 !important; font-weight: 600; color: #047857; }
.med-col { font-size: 11px; color: var(--muted); }
.prov-col { min-width: 88px; font-size: 11px !important; }
.table-wrap-wide { max-width: 100%; }
.compare-tco-wide { min-width: 720px; }
.calc-label { display: block; margin: 8px 0; font-size: 14px; }
.calc-label input, .calc-label select, .wizard-select {
  margin-left: 8px; padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px;
}
.calc-check input { margin-right: 6px; }
.btn-primary {
  margin-top: 8px; background: var(--accent); color: #fff; border: none;
  padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: 600;
}
.btn-primary:hover { background: #4f46e5; }
.calc-result { display: block; margin-top: 12px; font-weight: 600; color: var(--ok); font-size: 15px; }
.user-wizard .wizard-h { margin: 16px 0 8px; font-size: 15px; color: var(--brand); }
.faq { margin: 8px 0; border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; background: #fafafa; }
.faq summary { cursor: pointer; font-weight: 500; }
.tour-steps, .wizard-steps { padding-left: 20px; }
.deck-footer {
  text-align: center; font-size: 12px; padding: 24px 20px; color: var(--muted);
  border-top: 1px solid var(--border); background: var(--surface);
}
.compare-table thead th { white-space: normal; line-height: 1.35; min-width: 72px; vertical-align: bottom; }
.compare-tco th, .compare-tco td { font-size: 12px; }
.matrix-compact th, .matrix-compact td { font-size: 12px; white-space: normal; line-height: 1.35; }
.matrix-compact th[scope=row] { min-width: 140px; font-weight: 600; }
.tier-tag { display: inline-block; font-size: 10px; font-weight: 700; color: #6366f1; background: #eef2ff; padding: 1px 6px; border-radius: 4px; margin-top: 2px; }
.matrix-focus-wide { overflow-x: auto; }
.matrix-focus-wide .matrix-compact th, .matrix-focus-wide .matrix-compact td { font-size: 11px; min-width: 72px; }
.matrix-full th { max-width: 88px; white-space: normal; line-height: 1.25; font-size: 11px; }
.cap-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin: 16px 0; }
.cap-card { background: #f9fafb; border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; }
.cap-card h4 { margin: 0 0 4px; font-size: 14px; color: var(--brand); }
.cap-status { font-size: 11px; font-weight: 600; color: #4338ca; margin: 0 0 6px; }
.cap-card p { margin: 0; font-size: 13px; color: #374151; }
.overlap-list li { margin: 6px 0; }
.units-box, .method-box {
  background: #f8fafc; border: 1px solid var(--border); border-radius: 8px;
  padding: 12px 14px; margin: 12px 0; font-size: 13px;
}
.units-box h4, .method-box h4 { margin: 0 0 8px; font-size: 14px; color: var(--brand); }
.units-dl { margin: 0; display: grid; grid-template-columns: 100px 1fr; gap: 6px 12px; }
.units-dl dt { font-weight: 600; color: #4338ca; }
.units-dl dd { margin: 0; }
.tco-chart { margin: 16px 0; padding: 12px; background: #fafafa; border-radius: 8px; border: 1px solid var(--border); }
.tco-legend { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; font-size: 12px; color: #374151; }
.swatch { display: inline-block; width: 14px; height: 14px; border-radius: 3px; margin-right: 6px; vertical-align: middle; }
.swatch-comp { background: #6366f1; }
.swatch-korus { background: #22c55e; }
.tco-row { display: grid; grid-template-columns: minmax(120px, 180px) 1fr minmax(100px, 130px); gap: 10px; align-items: center; margin: 10px 0; }
.tco-label { font-size: 12px; line-height: 1.3; }
.tco-ru { display: block; color: var(--muted); font-size: 11px; }
.tco-bars { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.bar-comp, .bar-korus { height: 14px; border-radius: 4px; min-width: 4px; }
.bar-comp { background: #6366f1; }
.bar-korus { background: #22c55e; }
.tco-vals { font-size: 11px; line-height: 1.4; text-align: right; }
.calc-result { font-weight: 400; color: var(--text); font-size: 14px; }
.calc-result strong { color: var(--brand); }
.calc-breakdown { width: 100%; margin-top: 10px; font-size: 13px; border-collapse: collapse; }
.calc-breakdown th, .calc-breakdown td { border: 1px solid var(--border); padding: 6px 8px; text-align: left; }
.calc-breakdown th { background: #eef2ff; }
.donut-chart {
  background: #fff; border: 1px solid var(--border); border-radius: var(--radius);
  padding: 16px 18px; max-width: 420px;
}
.donut-title { font-weight: 700; color: var(--brand); margin: 0 0 12px; font-size: 15px; }
.donut-layout { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.donut-ring {
  width: 120px; height: 120px; border-radius: 50%; flex-shrink: 0;
  position: relative;
}
.donut-ring::after {
  content: ""; position: absolute; inset: 22%; background: #fff; border-radius: 50%;
}
.donut-legend { list-style: none; margin: 0; padding: 0; font-size: 13px; flex: 1; min-width: 180px; }
.donut-legend li { margin: 6px 0; display: flex; align-items: baseline; gap: 8px; }
.donut-swatch { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; display: inline-block; }
.factors-out { color: #4b5563; font-size: 13px; }
.delta-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 16px 0;
}
.delta-col {
  background: #f9fafb; border: 1px solid var(--border); border-radius: 8px; padding: 14px 16px;
}
.delta-korus { border-left: 4px solid #22c55e; }
.delta-peer { border-left: 4px solid #6366f1; }
.delta-col h4 { margin: 0 0 10px; font-size: 15px; color: var(--brand); }
.delta-col li { font-size: 13px; margin: 6px 0; }
@media (max-width: 640px) {
  .delta-grid { grid-template-columns: 1fr; }
}
.provider-block { margin-top: 14px; padding-top: 10px; border-top: 1px dashed var(--border); }
.provider-block h5 { margin: 0 0 6px; font-size: 14px; color: var(--brand); }
@media (max-width: 640px) {
  .tab-bar { flex-direction: column; align-items: stretch; }
  .tab-bar button { width: 100%; }
  .block0-grid { grid-template-columns: 1fr; }
  .subsection, .tab-intro, .sub-nav { margin-left: 12px; margin-right: 12px; }
  .tco-row { grid-template-columns: 1fr; }
  .tco-vals { text-align: left; }
  .units-dl { grid-template-columns: 1fr; }
}
@media (max-width: 375px) {
  .block-0 h1 { font-size: 22px; }
  .tab-bar button { font-size: 13px; }
}
@media print {
  .tab-bar, .sub-nav, .btn-primary, .btn-calc { display: none; }
  .tab-panel { display: block !important; page-break-before: always; }
}
"""


def _deck_js() -> str:
    return """
(function(){
  const tabs = document.querySelectorAll('[role="tab"]');
  const panels = document.querySelectorAll('[role="tabpanel"]');
  function activate(id){
    tabs.forEach(t=>{ t.setAttribute('aria-selected', t.dataset.tab===id?'true':'false'); });
    panels.forEach(p=>{ p.classList.toggle('active', p.id==='tab-'+id); });
    location.hash = id;
  }
  tabs.forEach(t=> t.addEventListener('click', ()=> activate(t.dataset.tab)));
  document.addEventListener('keydown', e=>{
    if(!e.target.matches('[role="tab"]')) return;
    const i=[...tabs].indexOf(e.target);
    if(e.key==='ArrowRight') tabs[(i+1)%tabs.length].focus();
    if(e.key==='ArrowLeft') tabs[(i-1+tabs.length)%tabs.length].focus();
  });
  const hash=(location.hash||'#pm').slice(1);
  const tabId=['pm','tech','sales','user'].includes(hash.split('-')[0])?hash.split('-')[0]:'pm';
  activate(tabId);

  const data=JSON.parse(document.getElementById('deck-data').textContent);
  function fmt(n){ return n.toString().replace(/\\B(?=(\\d{3})+(?!\\d))/g,' ')+' ₽'; }

  function estimateRam(ru){
    const anchors=data.ram_anchors;
    if(ru<=anchors[0][0]) return anchors[0][1];
    for(let i=0;i<anchors.length-1;i++){
      const [r0,g0]=anchors[i], [r1,g1]=anchors[i+1];
      if(ru<=r1){
        const t=(Math.log(ru)-Math.log(r0))/(Math.log(r1)-Math.log(r0));
        return Math.max(g0, Math.round(g0+t*(g1-g0)));
      }
    }
    return anchors[anchors.length-1][1];
  }
  function vmTier(ram){
    for(const t of data.vm_tiers) if(ram<=t) return t;
    return data.vm_tiers[data.vm_tiers.length-1];
  }
  function activityRate(ru){
    if(ru<=10000) return 0.5;
    if(ru<=100000) return 0.45;
    if(ru<=500000) return 0.35;
    return 0.25;
  }
  function peakOnlineRate(ru){
    if(ru<=10000) return 0.15;
    if(ru<=100000) return 0.12;
    return 0.10;
  }
  function derivePeakOnline(ru, po){
    if(po>0) return po;
    return Math.max(1, Math.round(ru*activityRate(ru)*peakOnlineRate(ru)));
  }
  function derivePeakMsgS(ru, pms, mpd, po){
    if(pms>0) return pms;
    const dau=Math.max(1, Math.round(ru*activityRate(ru)));
    const avg=(dau*mpd)/86400;
    return Math.max(0.5, avg*(data.load_defaults?.peak_burst||3.5));
  }
  function scaleModVcpu(id, base, ru, po, pms){
    if(id==='core-api') return Math.max(base, 2+pms/12+po/2500);
    if(id==='worker-message-pipeline') return Math.max(base, 2+pms/18);
    if(id==='livekit') return Math.max(base, 2+po/5000);
    if(id==='ws-gateway') return Math.max(base, 1+po/2000);
    if(id==='nats') return Math.max(base, 1+pms/30);
    if(id==='postgres-hot') return Math.max(base, 1+pms/25+ru/12000);
    if(id==='solr') return Math.max(base, 1+ru/20000);
    return base;
  }
  function scaleModRam(id, base, ru, po, pms){
    if(id==='postgres-hot') return base+ru/8000+pms/8;
    if(id==='core-api') return base+po/400+pms/1.5;
    if(id==='ws-gateway') return base+po/800;
    if(id==='worker-message-pipeline') return base+pms/2.5;
    if(id==='worker-push') return base+po/3000;
    if(id==='livekit') return base+po/4000;
    if(id==='nats') return base+pms/20;
    if(id==='solr') return base+ru/15000;
    if(id==='minio') return base+ru/20000;
    return base;
  }
  function backupProfile(key){
    return (data.backup_profiles||{})[key]||{disk_mult:1, ram_gb:0, ops_mult:1, label:''};
  }
  function readBackupParams(){
    const ram=+document.getElementById('calc-tech-mod-backup-ram')?.value||0;
    const disk=+document.getElementById('calc-tech-mod-backup-disk')?.value||1;
    const ops=+document.getElementById('calc-tech-mod-backup-ops')?.value||1;
    let label='custom';
    if(ram===0&&disk===1&&ops===1) label='без отдельного бэка-контура';
    else if(ram===2&&disk===1.35&&ops===1.4) label='standard (снапшоты)';
    else if(ram===8&&disk===2&&ops===2) label='DR';
    else label='RAM +'+ram+' ГБ · диск ×'+disk+' · ops ×'+ops;
    return {disk_mult:disk, ram_gb:ram, ops_mult:ops, label:label};
  }
  function collectModuleReplicas(scope){
    const reps={};
    (scope||document).querySelectorAll('.calc-mod-rep').forEach(el=>{
      const max=+(el.dataset.max||el.max||99);
      reps[el.dataset.mod]=Math.min(max, Math.max(1, +el.value||1));
    });
    return reps;
  }
  function capContext(){
    const scope=document.getElementById('calc-tech-cap');
    return {
      scope,
      enabled: enabledModulesFromScope(scope),
      replicas: collectModuleReplicas(scope),
      plugins: +document.getElementById('calc-tech-mod-plugins')?.value||0,
      ssdTb: +document.getElementById('calc-tech-mod-ssd')?.value||0,
      hddTb: +document.getElementById('calc-tech-mod-hdd')?.value||0,
      ha: !!document.getElementById('calc-tech-mod-ha')?.checked,
      backupParams: readBackupParams(),
    };
  }
  function resolveReplicaCount(spec, reps, ha, appN, webN, plugins){
    if(spec.per_plugin) return Math.max(0, plugins);
    if(reps[spec.id]) return Math.min(spec.max_replicas, Math.max(1, reps[spec.id]));
    if(spec.id==='core-api') return Math.min(spec.max_replicas, appN);
    if(spec.id==='web-lb') return Math.min(spec.max_replicas, webN);
    if(spec.id==='worker-message-pipeline') return Math.min(spec.max_replicas, reps['worker-message-pipeline']||appN);
    if(spec.id==='worker-push') return Math.min(spec.max_replicas, reps['worker-push']||Math.max(1, Math.floor(appN/2)));
    if(ha && ['postgres-hot','postgres-archive','redis','nats','keycloak'].includes(spec.id))
      return Math.min(spec.max_replicas, 2);
    if(spec.id==='zookeeper' && (reps.solr||1)>=3) return 3;
    if(spec.id==='solr' && (reps.solr||1)>=3) return 3;
    return 1;
  }
  function appNodeCount(pms, po, ha, ru){
    ru=ru||0;
    let n=1;
    if(pms>30||po>3000) n=2;
    if(pms>120||po>12000) n=3;
    if(pms>400) n=Math.max(n,6);
    if(ru>50000) n=Math.max(n,2);
    if(ru>200000) n=Math.max(n,4);
    if(ru>500000) n=Math.max(n,6);
    if(ha) n=Math.max(n,2);
    return Math.min(n, 6);
  }
  function moduleResourceTotals(ramUnit, vcpuUnit, count){
    return {ramGb: Math.ceil(ramUnit)*count, vcpu: Math.ceil(vcpuUnit)*count};
  }
  function splitInstances(spec, ramDemand, vcpuDemand, baseCount, ha){
    const ramCap=spec.instance_ram_cap_gb||Math.max(spec.ram_gb*4, 8);
    const vcpuCap=spec.instance_vcpu_cap||Math.max(spec.vcpu*4, 4);
    const split=Math.max(1,
      ramDemand>ramCap?Math.ceil(ramDemand/ramCap):1,
      vcpuDemand>vcpuCap?Math.ceil(vcpuDemand/vcpuCap):1);
    if(spec.replica_mode==='active'){
      const count=Math.min(spec.max_replicas, Math.max(baseCount, split));
      return {count, ramUnit:ramDemand/count, vcpuUnit:vcpuDemand/count};
    }
    if(spec.replica_mode==='cluster'){
      let count=Math.max(baseCount, split);
      if((ha||split>1) && spec.max_replicas>=3) count=Math.max(count, 3);
      count=Math.min(spec.max_replicas, Math.max(1, count));
      return {count, ramUnit:ramDemand/count, vcpuUnit:vcpuDemand/count};
    }
    let count=baseCount;
    if(split>1) count=Math.max(count, Math.min(spec.max_replicas, split));
    if(ha && spec.max_replicas>=2) count=Math.max(count, Math.min(2, spec.max_replicas));
    count=Math.min(spec.max_replicas, Math.max(1, count));
    if(ha && count>=2 && split<=1) return {count, ramUnit:ramDemand, vcpuUnit:vcpuDemand};
    return {count, ramUnit:ramDemand/count, vcpuUnit:vcpuDemand/count};
  }
  function webNodeCount(po, ha){
    let n= po<=5000?1:2;
    if(po>20000) n=Math.max(n,3);
    if(ha) n=Math.max(n,2);
    return Math.min(n, 4);
  }
  function storageTb(ru, mpd, gbUser, retY, bp){
    const diskMult=(bp&&bp.disk_mult)||1;
    const dau=Math.max(1, Math.round(ru*activityRate(ru)));
    const msgsDay=dau*mpd;
    const msgGbYr=msgsDay*365*2048/(1024**3);
    const filesGbYr=ru*gbUser;
    const totalGb=(msgGbYr+filesGbYr)*retY;
    return {
      ssd: Math.round((0.5+totalGb*0.15/1024)*diskMult*100)/100,
      hdd: Math.round(Math.max(2, totalGb/1024)*diskMult*100)/100,
      diskMult
    };
  }
  function capacityFromRam(spec, count, ramUnit){
    const ram=ramUnit!=null?ramUnit:spec.ram_gb;
    const mult=(spec.replica_mode==='active')?count:1;
    if(spec.id==='ws-gateway') return {po:Math.max(100, Math.floor((ram-4)*800))*mult, pms:0, ru:0};
    if(spec.id==='core-api') return {po:Math.max(100, Math.floor((ram-8)*400))*mult, pms:Math.max(1,(ram-8)*1.5)*mult, ru:0};
    if(spec.id==='worker-message-pipeline') return {po:0, pms:Math.max(1,(ram-4)*2.5)*mult, ru:0};
    if(spec.id==='livekit') return {po:Math.max(100, Math.floor((ram-4)*500))*mult, pms:0, ru:0};
    if(spec.id==='web-lb') return {po:Math.max(100, Math.floor((ram-4)*500))*mult, pms:0, ru:0};
    if(spec.id==='postgres-hot') return {po:0, pms:0, ru:Math.max(1000, Math.floor((ram-4)*8000))};
    return {po:0, pms:0, ru:0};
  }
  function capacityFromVcpu(spec, count, vcpuUnit){
    const v=vcpuUnit!=null?vcpuUnit:spec.vcpu;
    const mult=(spec.replica_mode==='active')?count:1;
    if(spec.id==='ws-gateway') return {po:Math.max(100, Math.floor((v-1)*400))*mult, pms:0, ru:0};
    if(spec.id==='core-api') return {po:Math.max(100, Math.floor((v-1)*250))*mult, pms:Math.max(1,(v-1)*12)*mult, ru:0};
    if(spec.id==='worker-message-pipeline') return {po:0, pms:Math.max(1,(v-1)*15)*mult, ru:0};
    if(spec.id==='livekit') return {po:Math.max(100, Math.floor((v-1)*500))*mult, pms:0, ru:0};
    if(spec.id==='web-lb') return {po:Math.max(100, Math.floor((v-1)*350))*mult, pms:0, ru:0};
    if(spec.id==='postgres-hot') return {po:0, pms:0, ru:Math.max(1000, Math.floor((v-1)*10000))};
    if(spec.id==='nats') return {po:0, pms:Math.max(1,(v-1)*20)*mult, ru:0};
    return {po:0, pms:0, ru:0};
  }
  function mergeCapacity(r, c){
    const pick=(a,b)=> (a&&b)?Math.min(a,b):(a||b||0);
    return {po:pick(r.po,c.po), pms:pick(r.pms,c.pms), ru:pick(r.ru,c.ru),
      vcpuBound: (c.po&&r.po&&c.po<r.po)||(c.pms&&r.pms&&c.pms<r.pms)||(c.ru&&r.ru&&c.ru<r.ru)};
  }
  function requiredModuleIds(){
    return new Set((data.modules||[]).filter(m=>m.required).map(m=>m.id));
  }
  function defaultEnabledModuleIds(){
    return new Set((data.modules||[]).filter(m=>m.required||m.default_enabled!==false).map(m=>m.id));
  }
  function normalizeEnabledIds(raw){
    const out=new Set(raw);
    requiredModuleIds().forEach(id=>out.add(id));
    if(!out.has('solr')) out.delete('zookeeper');
    (data.modules||[]).forEach(m=>{
      if(!out.has(m.id)||!m.requires||!m.requires.length) return;
      if(!m.requires.every(r=>out.has(r))) out.delete(m.id);
    });
    return out;
  }
  function enabledModulesFromScope(scope){
    const enabled=new Set(requiredModuleIds());
    scope.querySelectorAll('.calc-mod-opt:checked:not(:disabled)').forEach(el=>enabled.add(el.dataset.mod));
    return normalizeEnabledIds(enabled);
  }
  function syncModuleDependencies(scope){
    if(!scope) return;
    (data.modules||[]).forEach(m=>{
      if(!m.requires||!m.requires.length) return;
      const cb=scope.querySelector('.calc-mod-opt[data-mod="'+m.id+'"]');
      if(!cb) return;
      const ok=m.requires.every(r=>{
        const dep=scope.querySelector('.calc-mod[data-mod="'+r+'"]');
        return dep && dep.checked;
      });
      cb.disabled=!ok;
      if(!ok) cb.checked=false;
      cb.closest('tr')?.classList.toggle('calc-mod-dep-off', !ok);
    });
  }
  function estimateFromLoad(inp){
    const ru=Math.max(1, inp.ru);
    const po=derivePeakOnline(ru, inp.peakOnline);
    const pms=derivePeakMsgS(ru, inp.peakMsgS, inp.msgsDay, po);
    const mods=data.modules||[];
    const enabledNorm=normalizeEnabledIds(inp.enabled||defaultEnabledModuleIds());
    const reps=inp.replicas||{};
    const bp=inp.backupParams||backupProfile(inp.backup||'none');
    let appN=appNodeCount(pms, po, inp.ha, ru);
    let webN=webNodeCount(po, inp.ha);
    const instances=[];
    let totalRam=0, totalVcpu=0;
    for(const spec of mods){
      if(!enabledNorm.has(spec.id)) continue;
      const baseCount=resolveReplicaCount(spec, reps, inp.ha, appN, webN, inp.plugins||0);
      if(spec.per_plugin && baseCount===0) continue;
      const ramDemand=scaleModRam(spec.id, spec.ram_gb, ru, po, pms);
      const vcpuDemand=scaleModVcpu(spec.id, spec.vcpu, ru, po, pms);
      const split=splitInstances(spec, ramDemand, vcpuDemand, baseCount, inp.ha);
      let ram=split.ramUnit;
      let vcpu=split.vcpuUnit;
      const count=split.count;
      if(spec.replica_mode==='cluster' && count>1) ram*=1.05;
      const tot=moduleResourceTotals(ram, vcpu, count);
      instances.push({id:spec.id, label:spec.label, count, ramGb:tot.ramGb, vcpu:tot.vcpu, mode:spec.replica_mode});
      totalRam+=tot.ramGb;
      totalVcpu+=tot.vcpu;
    }
    totalRam=Math.ceil(totalRam+bp.ram_gb);
    totalVcpu=Math.ceil(totalVcpu);
    const stor=storageTb(ru, inp.msgsDay, inp.gbUser, inp.retention, bp);
    let channel= po<8000?200:1000;
    if(bp.disk_mult>=2) channel=Math.max(channel, 400);
    return {
      ru, peakOnline:po, peakMsgS:Math.round(pms*10)/10,
      dau:Math.max(1, Math.round(ru*activityRate(ru))),
      modules:instances, totalRam, totalVcpu,
      ssdTb:stor.ssd, hddTb:stor.hdd, backupRam:bp.ram_gb,
      backupLabel: bp.label,
      backupParams: bp,
      channel, appNodes:appN, webNodes:webN
    };
  }
  function loadInpFromForm(){
    const ctx=capContext();
    return {
      ru:+document.getElementById('calc-tech-ru').value||1,
      peakOnline:+document.getElementById('calc-tech-peak-online').value||0,
      peakMsgS:+document.getElementById('calc-tech-peak-msg').value||0,
      msgsDay:+document.getElementById('calc-tech-msgs-day').value||40,
      gbUser:+document.getElementById('calc-tech-gb-user').value||0.5,
      retention:+document.getElementById('calc-tech-retention').value||3,
      ha: ctx.ha,
      plugins: ctx.plugins,
      enabled: ctx.enabled,
      replicas: ctx.replicas,
      backupParams: ctx.backupParams
    };
  }
  function quoteProviderLoad(inp, p){
    const le=estimateFromLoad(inp);
    const ramBilled=vmTier(le.totalRam);
    const ramCost=ramBilled*p.ram_rub_gb;
    const vcpuCost=le.totalVcpu*p.vcpu_rub;
    const vmCompute=ramCost+vcpuCost;
    const disk=Math.round(le.ssdTb*p.ssd_tb_rub + le.hddTb*p.hdd_tb_rub);
    const channel= le.channel<=200?p.channel_200:p.channel_1g;
    const opsMult=(le.backupParams&&le.backupParams.ops_mult)||backupProfile(le.backup||'none').ops_mult||1;
    const ops=Math.round(p.ops_base*(le.ru<50000?1:2)*opsMult);
    const lines=[
      ['Серверы (prod full)', vmCompute],
      ['Диски (+ бэкап)', disk],
      ['Канал', channel],
      ['Backup / мониторинг', ops]
    ];
    const monthly=lines.reduce((s,x)=>s+x[1],0);
    return {provider:p, monthly, yearly:monthly*12, lines, load:le, ramBilled, ramCost, vcpuCost};
  }
  function renderModuleTable(mods){
    const rows=mods.map(m=>'<tr><td>'+m.label+'</td><td>'+m.count+'</td><td>'+m.ramGb+'</td><td>'+m.vcpu+'</td><td>'+(m.mode||'')+'</td></tr>').join('');
    return '<div class="table-wrap"><table class="feature-table"><thead><tr><th>Модуль</th><th>×</th><th>RAM ГБ</th><th>vCPU</th><th>Тип</th></tr></thead><tbody>'+rows+'</tbody></table></div>'+
      '<p class="small calc-mod-hint">× — число VM/узлов; RAM и vCPU — суммарно по строке (при превышении лимита узла — scale-out).</p>';
  }
  function estimateCapacityFromModules(enabled, plugins, ssdTb, hddTb, backupParams, replicas, ha){
    const specs=data.modules||[];
    const specMap={}; specs.forEach(s=>specMap[s.id]=s);
    const reps=replicas||{};
    const bp=backupParams||{disk_mult:1, ram_gb:0, ops_mult:1, label:''};
    let totalRam=0, totalVcpu=0;
    let maxPo=100000, maxPms=10000, maxRu=1000000;
    const bnVcpu=[];
    const modulesOut=[];
    normalizeEnabledIds(enabled).forEach(id=>{
      const s=specMap[id]; if(!s) return;
      const count=resolveReplicaCount(s, reps, !!ha, 1, 1, plugins);
      if(s.per_plugin && count===0) return;
      let ram=s.ram_gb;
      let vcpu=s.vcpu;
      if(s.replica_mode==='cluster' && count>1) ram*=1.05;
      const tot=moduleResourceTotals(ram, vcpu, count);
      totalRam+=tot.ramGb;
      totalVcpu+=tot.vcpu;
      modulesOut.push({label:s.label, count, ramGb:tot.ramGb, vcpu:tot.vcpu, mode:s.replica_mode});
      const cap=mergeCapacity(capacityFromRam(s, count, ram), capacityFromVcpu(s, count, vcpu));
      if(cap.po) maxPo=Math.min(maxPo, cap.po);
      if(cap.pms) maxPms=Math.min(maxPms, cap.pms);
      if(cap.ru) maxRu=Math.min(maxRu, cap.ru);
      if(cap.vcpuBound) bnVcpu.push(s.label);
    });
    totalRam=Math.ceil(totalRam+bp.ram_gb);
    totalVcpu=Math.ceil(totalVcpu);
    const needHdd=storageTb(10000, 40, 0.5, 3, {disk_mult:1}).hdd;
    const effHdd= bp.disk_mult>0? hddTb/bp.disk_mult : hddTb;
    const storY= needHdd>0? Math.round(effHdd/needHdd*3*10)/10 : 0;
    return {totalRam, totalVcpu, ssdTb:Math.round(ssdTb*bp.disk_mult*100)/100, hddTb, maxRu, maxPo, maxPms:Math.round(maxPms*10)/10, storY, backupRam:bp.ram_gb, backupLabel:bp.label, backupParams:bp, modules:modulesOut, bottleneck: bnVcpu.length?('vCPU: '+bnVcpu.slice(0,3).join(', ')):'RAM'};
  }
  function median(vals){
    const s=[...vals].sort((a,b)=>a-b);
    const m=Math.floor(s.length/2);
    return s.length%2?s[m]:Math.round((s[m-1]+s[m])/2);
  }
  function renderProviderCards(quotes){
    const minM=Math.min(...quotes.map(q=>q.monthly));
    return '<div class="calc-provider-grid">'+quotes.map(q=>{
      const isMin=q.monthly===minM;
      let rows=q.lines.map(l=>'<tr><td>'+l[0]+'</td><td>'+fmt(l[1])+'</td></tr>').join('');
      return '<article class="calc-provider-card'+(isMin?' is-min':'')+'">'+
        '<div class="calc-provider-head"><span>'+q.provider.label+(isMin?' · мин.':'')+'</span>'+
        '<a href="'+q.provider.pricing_url+'" rel="noopener" target="_blank">прайс ↗</a></div>'+
        '<div class="calc-provider-total">'+fmt(q.monthly)+'/мес · '+fmt(q.yearly)+'/год</div>'+
        '<table class="calc-breakdown"><tbody>'+rows+'</tbody></table></article>';
    }).join('')+'</div>';
  }
  function renderHero(stats){
    return '<div class="calc-hero">'+stats.map(s=>'<div class="calc-stat"><div class="calc-stat-val">'+
      s.val+'</div><div class="calc-stat-label">'+s.label+'</div></div>').join('')+'</div>';
  }
  function supportUpdatesFte(ru, include, m){
    if(!include) return 0;
    if(ru<5000) return m.updates_fte.lt5k;
    if(ru<50000) return m.updates_fte.lt50k;
    return m.updates_fte.gte50k;
  }
  function computeSupport(inp){
    const m=data.support||{};
    const fb=m.fte_base||{base:0.15,divisor:80000,cap:4};
    let base=fb.base+inp.ru/fb.divisor; if(base>fb.cap) base=fb.cap;
    const lines=[
      ['База (масштаб RU)', base],
      ['Обновления релизов', supportUpdatesFte(inp.ru, inp.upd, m)],
      ['Топология', (m.topology_fte||{})[inp.topo]||0],
      ['Интеграции', (m.integrations_fte||{})[inp.integ]||0],
      ['Организации / филиалы', (m.orgs_fte||{})[inp.orgs]||0],
      ['L1 (первая линия)', (m.l1_fte||{})[inp.l1]||0],
      ['Обучение', (m.training_fte||{})[inp.training]||0],
      ['E2EE / MLS', (m.e2ee_fte||{})[inp.e2ee]||0],
      ['ИБ / комплаенс', (m.compliance_fte||{})[inp.compliance]||0],
      ['Резерв / DR', (m.dr_fte||{})[inp.dr]||0]
    ];
    const sub=lines.reduce((s,x)=>s+x[1],0);
    const modeMult=(m.support_mode_mult||{})[inp.sla]||1;
    const teamMult=(m.team_mult||{})[inp.team]||1;
    const fte=sub*modeMult*teamMult;
    let rate=(m.base_rate_rub||180000);
    rate*=((m.staffing_rate_mult||{})[inp.staffing]||1);
    rate*=((m.region_rate_mult||{})[inp.region]||1);
    rate=Math.round(rate);
    const overhead=inp.overhead?(m.overhead_pct||0):0;
    const monthly=Math.round(fte*rate*(1+overhead));
    return {lines, sub, modeMult, teamMult, fte, rate, overhead, monthly};
  }

  function renderCostRail(quotes){
    const med=median(quotes.map(q=>q.monthly));
    const minM=Math.min(...quotes.map(q=>q.monthly));
    const q0=quotes[0];
    const ramMed=Math.round(quotes.reduce((s,q)=>s+q.ramCost,0)/quotes.length);
    const vcpuMed=Math.round(quotes.reduce((s,q)=>s+q.vcpuCost,0)/quotes.length);
    let list=quotes.map(q=>'<li'+(q.monthly===minM?' class="is-min"':'')+'><span>'+q.provider.label+
      '</span><strong>'+fmt(q.monthly)+'</strong></li>').join('');
    return '<aside class="calc-cost-rail"><h5>Смета infra</h5>'+
      '<div class="calc-cost-median">'+fmt(med)+'<span>медиана / мес · '+fmt(med*12)+'/год</span></div>'+
      '<p class="small">Compute: RAM ~'+fmt(ramMed)+' + vCPU ~'+fmt(vcpuMed)+'</p>'+
      '<ul class="calc-cost-list">'+list+'</ul>'+
      '<p class="small">Без лицензии, TURN/VKS. <a href="#price-sources">Прайсы</a>.</p></aside>';
  }

  function runSales(){
    const ru=+document.getElementById('calc-sales-ru').value||1;
    const inp={ru, peakOnline:0, peakMsgS:0, msgsDay:40, gbUser:0.5, retention:3, ha:false, plugins:0};
    const quotes=data.providers.map(p=>quoteProviderLoad(inp,p));
    const med=median(quotes.map(q=>q.monthly));
    const le=quotes[0].load;
    document.getElementById('calc-sales-out').innerHTML=
      renderHero([
        {val:ru.toLocaleString('ru-RU'), label:'рег.'},
        {val:le.totalRam+' ГБ', label:'RAM prod full'},
        {val:fmt(med), label:'медиана / мес'},
        {val:fmt(med*12), label:'медиана / год'}
      ])+renderProviderCards(quotes)+
      '<p class="small">Prod full, авто-нагрузка по модели. На 1 рег.: ~'+(med/ru).toFixed(2)+' ₽/мес. Без лицензии, TURN/VKS. '
      +'<a href="#price-sources">Источники цен</a>.</p>';
  }
  function runTechLoad(){
    const inp=loadInpFromForm();
    const quotes=data.providers.map(p=>quoteProviderLoad(inp,p));
    const le=quotes[0].load;
    document.getElementById('calc-tech-res-out').innerHTML=
      renderHero([
        {val:le.totalRam+' ГБ', label:'RAM суммарно'},
        {val:String(le.totalVcpu), label:'vCPU'},
        {val:le.appNodes+' app · '+le.webNodes+' web', label:'узлы'},
        {val:le.ssdTb+' / '+le.hddTb+' ТБ', label:'SSD / HDD'}
      ])+
      '<div class="calc-res-split"><div class="calc-res-main">'+
      '<div class="calc-assumptions"><p class="small"><strong>Нагрузка:</strong> RU '+le.ru.toLocaleString('ru-RU')+
      ' · DAU ~'+le.dau+' · пик онлайн '+le.peakOnline+' · пик msg/s '+le.peakMsgS+
      ' · канал '+le.channel+' Мбит/с · бэкап '+le.backupLabel+(le.backupRam?(' (+'+le.backupRam+' ГБ RAM)'):'')+'</p></div>'+
      renderModuleTable(le.modules)+'</div>'+renderCostRail(quotes)+'</div>';
  }
  function runTechModules(){
    const ctx=capContext();
    const cap=estimateCapacityFromModules(ctx.enabled, ctx.plugins, ctx.ssdTb, ctx.hddTb, ctx.backupParams, ctx.replicas, ctx.ha);
    const quoteRu=+document.getElementById('calc-tech-cap-quote-ru')?.value||0;
    const ruForQuote= quoteRu>0 ? quoteRu : cap.maxRu;
    const loadInp={
      ru: ruForQuote, peakOnline:0, peakMsgS:0, msgsDay:40, gbUser:0.5, retention:3,
      ha: ctx.ha, plugins: ctx.plugins, enabled: ctx.enabled, replicas: ctx.replicas, backupParams: ctx.backupParams
    };
    const quotes=data.providers.map(p=>quoteProviderLoad(loadInp,p));
    const modRows=cap.modules.map(m=>'<tr><td>'+m.label+'</td><td>'+m.count+'</td><td>'+m.ramGb+'</td><td>'+m.vcpu+'</td><td>'+(m.mode||'')+'</td></tr>').join('');
    const modTable='<div class="table-wrap"><table class="feature-table"><thead><tr><th>Модуль</th><th>×</th><th>RAM ГБ</th><th>vCPU</th><th>Тип</th></tr></thead><tbody>'+modRows+'</tbody></table></div>';
    document.getElementById('calc-tech-cap-out').innerHTML=
      '<div class="calc-user-hero">'+renderHero([
        {val:cap.maxRu.toLocaleString('ru-RU'), label:'макс. рег. пользователей'},
        {val:cap.maxPo.toLocaleString('ru-RU'), label:'пик онлайн'},
        {val:String(cap.maxPms), label:'пик msg/s'},
        {val:cap.storY+' лет', label:'HDD @10k RU'}
      ])+'</div>'+
      '<div class="calc-res-split"><div class="calc-res-main">'+
      '<div class="calc-assumptions"><p class="small"><strong>Железо:</strong> RAM '+cap.totalRam+
      ' ГБ · vCPU '+cap.totalVcpu+' · SSD '+cap.ssdTb+' ТБ · HDD '+ctx.hddTb+' ТБ</p>'+
      '<p class="small"><strong>Смета при RU:</strong> '+ruForQuote.toLocaleString('ru-RU')+
      (quoteRu>0?' (задано)':' (макс. по модулям)')+' · бэкап: '+cap.backupLabel+'</p>'+
      '<p class="small"><strong>Узкое место:</strong> '+cap.bottleneck+'</p></div>'+
      modTable+'</div>'+renderCostRail(quotes)+'</div>';
  }
  function bindTechCalcLive(){
    let t=null;
    const rerun=()=>{ clearTimeout(t); t=setTimeout(()=>{ runTechModules(); runTechLoad(); }, 180); };
    ['calc-tech-cap','calc-tech-res'].forEach(id=>{
      const el=document.getElementById(id);
      if(!el) return;
      el.querySelectorAll('input').forEach(n=>{
        n.addEventListener('input', rerun);
        n.addEventListener('change', rerun);
      });
    });
    document.querySelectorAll('.calc-preset').forEach(btn=>{
      btn.addEventListener('click', ()=>{
        document.getElementById('calc-tech-mod-backup-ram').value=btn.dataset.ram;
        document.getElementById('calc-tech-mod-backup-disk').value=btn.dataset.disk;
        document.getElementById('calc-tech-mod-backup-ops').value=btn.dataset.ops;
        rerun();
      });
    });
  }
  function runPm(){
    const inp={
      ru:+document.getElementById('calc-pm-ru').value||1,
      sla:document.getElementById('calc-pm-sla').value,
      upd:document.getElementById('calc-pm-upd').checked,
      topo:document.getElementById('calc-pm-topo').value,
      integ:document.getElementById('calc-pm-integ').value,
      team:document.getElementById('calc-pm-team').value,
      orgs:document.getElementById('calc-pm-orgs').value,
      l1:document.getElementById('calc-pm-l1').value,
      training:document.getElementById('calc-pm-training').value,
      e2ee:document.getElementById('calc-pm-e2ee').value,
      compliance:document.getElementById('calc-pm-compliance').value,
      dr:document.getElementById('calc-pm-dr').value,
      staffing:document.getElementById('calc-pm-staffing').value,
      region:document.getElementById('calc-pm-region').value,
      overhead:document.getElementById('calc-pm-overhead').checked
    };
    const r=computeSupport(inp);
    let body=r.lines.map(l=>'<tr><td>'+l[0]+'</td><td>'+(l[1]>0&&l[0]!=='База (масштаб RU)'?'+':'')+l[1].toFixed(2)+' FTE</td></tr>').join('');
    document.getElementById('calc-pm-out').innerHTML=
      renderHero([
        {val:r.fte.toFixed(2), label:'FTE итого'},
        {val:fmt(r.monthly), label:'₽ / мес'},
        {val:fmt(r.monthly*12), label:'₽ / год'},
        {val:fmt(r.rate), label:'ставка / FTE'}
      ])+
      '<table class="calc-breakdown"><tbody>'+body+
      '<tr><td>Сумма до режима</td><td>'+r.sub.toFixed(2)+' FTE</td></tr>'+
      '<tr><td>Режим поддержки</td><td>×'+r.modeMult+'</td></tr>'+
      '<tr><td>Модель команды</td><td>×'+r.teamMult+'</td></tr>'+
      (r.overhead?'<tr><td>Надбавка НДС/командировки</td><td>+'+(r.overhead*100)+'%</td></tr>':'')+
      '</tbody></table>'+
      '<p class="small">Полная модель сопровождения; не заменяет детальное КП.</p>';
  }

  document.getElementById('calc-sales-run')?.addEventListener('click', runSales);
  document.getElementById('calc-tech-res-run')?.addEventListener('click', runTechLoad);
  document.getElementById('calc-tech-cap-run')?.addEventListener('click', runTechModules);
  const capScope=document.getElementById('calc-tech-cap');
  capScope?.querySelectorAll('.calc-mod-opt,.calc-mod-rep').forEach(el=>{
    el.addEventListener('change', ()=>{ syncModuleDependencies(capScope); });
    el.addEventListener('input', ()=>{ syncModuleDependencies(capScope); });
  });
  syncModuleDependencies(capScope);
  bindTechCalcLive();
  document.getElementById('calc-pm-run')?.addEventListener('click', runPm);
  runSales(); runTechLoad(); runTechModules(); runPm();

  document.querySelector('a[href="#sales-s3"]')?.addEventListener('click', e=>{
    e.preventDefault(); activate('sales'); location.hash='sales-s3';
  });

  const wiz=document.getElementById('wizard-scenario');
  const steps=document.getElementById('wizard-steps');
  function renderWiz(){ const s=data.scenarios[wiz.value]||[]; steps.innerHTML=s.map(x=>'<li>'+x+'</li>').join(''); }
  wiz?.addEventListener('change', renderWiz); renderWiz();
})();
"""


def render_deck_html() -> str:
    build_day = date.today().isoformat()
    data = deck_data_json()
    footer = (
        f"{ps.DECK_VERSION} | {build_day} | PRICE_AS_OF {se.PRICE_AS_OF} | "
        f"offerings {data['offerings_max_as_of']} | Playwright {ps.PLAYWRIGHT_PASSED}/{ps.PLAYWRIGHT_TOTAL}"
    )
    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Korus Messenger — рабочий прототип</title>
<meta name="description" content="Корпоративный мессенджер, стадия рабочего прототипа. Не production."/>
<meta property="og:title" content="Korus Messenger — рабочий прототип"/>
<meta property="og:description" content="Корпоративный мессенджер, стадия рабочего прототипа. Не production."/>
<style>{_deck_css()}</style>
</head>
<body>
{render_block0()}
<nav class="tab-bar" role="tablist" aria-label="Вкладки презентации">
  <button role="tab" data-testid="deck-tab-pm" data-tab="pm" aria-selected="true" aria-controls="tab-pm">РП / аналитики</button>
  <button role="tab" data-testid="deck-tab-tech" data-tab="tech" aria-controls="tab-tech">Техническая</button>
  <button role="tab" data-testid="deck-tab-sales" data-tab="sales" aria-controls="tab-sales">Продажная</button>
  <button role="tab" data-testid="deck-tab-user" data-tab="user" aria-controls="tab-user">Пользовательская</button>
</nav>
<main>
  <div id="tab-pm" class="tab-panel active" role="tabpanel" data-testid="deck-panel-pm">{render_tab_pm()}</div>
  <div id="tab-tech" class="tab-panel" role="tabpanel" data-testid="deck-panel-tech">{render_tab_tech()}</div>
  <div id="tab-sales" class="tab-panel" role="tabpanel" data-testid="deck-panel-sales">{render_tab_sales()}</div>
  <div id="tab-user" class="tab-panel" role="tabpanel" data-testid="deck-panel-user">{render_tab_user()}</div>
</main>
<footer class="deck-footer">{escape(footer)}</footer>
<script type="application/json" id="deck-data">{json.dumps(data, ensure_ascii=False)}</script>
<script>{_deck_js()}</script>
</body>
</html>
"""
