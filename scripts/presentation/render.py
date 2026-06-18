"""Assemble self-contained HTML deck — 4 tabs, Block 0, calculators."""

from __future__ import annotations

import json
from datetime import date
from html import escape
from typing import Any

from scripts.presentation import content as cnt
from scripts.presentation import marketing as mkt
from scripts.presentation import product_status as ps
from scripts.presentation import sizing_pricing as sp
from scripts.presentation import visuals as viz
from scripts.presentation.compare_engine import build_all_rows, render_headroom_badge
from scripts.presentation.data_loader import load_competitors, load_offerings


def _fmt_rub(n: int | None) -> str:
    if n is None:
        return "—"
    return sp.fmt_rub(n)


def render_block0() -> str:
    blockers = "".join(f"<li>{escape(b)}</li>" for b in ps.PRODUCTION_BLOCKERS)
    done_rows = "".join(
        f"<tr><td>{escape(f[1])}</td><td>{ps.tag_html(f[2])}</td><td>{escape(f[3])}</td></tr>"
        for f in ps.features_by_status("done")
    )
    partial_rows = "".join(
        f"<tr><td>{escape(f[1])}</td><td>{ps.tag_html(f[2])}</td><td>{escape(f[3])}</td></tr>"
        for f in ps.features_by_status("partial")
    )
    planned_out = ps.features_by_status("planned") + ps.features_by_status("out")
    po_rows = "".join(
        f"<tr><td>{escape(f[1])}</td><td>{ps.tag_html(f[2])}</td><td>{escape(f[3])}</td></tr>"
        for f in planned_out
    )
    donut = viz.render_feature_donut_svg()
    return f"""
<section id="block-0" class="block-0 hero-warning" aria-label="Статус продукта">
  <h1>Korus Messenger — <span class="stage-label">{escape(ps.PRODUCT_STAGE_LABEL.lower())}</span></h1>
  <p class="disclaimer"><strong>Продукт не готов</strong> к промышленной эксплуатации (production).
  Демонстрирует архитектуру на <strong>лабораторном dev-стенде</strong>.</p>
  <p class="metrics">Playwright {ps.PLAYWRIGHT_PASSED}/{ps.PLAYWRIGHT_TOTAL} ({ps.PLAYWRIGHT_DATE});
  deck {ps.DECK_VERSION}; PRODUCTION_READY = false</p>
  <div class="block0-grid">
    <div>{donut}</div>
    <div>
      <h2>§0.1 Блокеры production</h2>
      <ul>{blockers}</ul>
    </div>
  </div>
  <h2>§0.2 Реализовано</h2>
  <table class="feature-table"><tr><th>Модуль</th><th>Статус</th><th>Примечание</th></tr>{done_rows}</table>
  <h2>§0.3 Частично</h2>
  <table class="feature-table"><tr><th>Модуль</th><th>Статус</th><th>Примечание</th></tr>{partial_rows}</table>
  <h2>§0.4 Не реализовано / вне scope</h2>
  <table class="feature-table"><tr><th>Модуль</th><th>Статус</th><th>Примечание</th></tr>{po_rows}</table>
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
        "<table class='compare-table'><tr><th>Продукт</th><th>Tier</th><th>Deploy</th>"
        f"<th>Offerings</th></tr>{''.join(rows)}</table>"
    )


def render_compare_table() -> str:
    rows_html = []
    for row in build_all_rows(load_offerings()):
        o = row.offering
        if not o.get("price_is_public", True):
            comp_cell = "нет публичного прайса"
        else:
            comp_cell = _fmt_rub(row.competitor_total_yearly_rub)
        badge = render_headroom_badge(row)
        src = escape(o["source_url"])
        rows_html.append(
            f"<tr><td>{escape(o['label'])} ({escape(o['product_id'])})</td>"
            f"<td>{o['value']:,}</td>".replace(",", " ")
            + f"<td>{comp_cell}</td>"
            f"<td>{_fmt_rub(row.korus_infra_yearly_rub)} {badge}</td>"
            f'<td><a href="{src}" rel="noopener">{src[:40]}…</a></td></tr>'
        )
    return (
        "<table class='compare-table'><tr><th>Тариф</th><th>RU</th><th>₽/год конкурент</th>"
        f"<th>Korus infra ₽/год</th><th>Источник</th></tr>{''.join(rows_html)}</table>"
        "<p class='footnote'>* headroom — без изменения цены/мощностей</p>"
    )


def render_feature_matrix() -> str:
    data = load_competitors()
    crit = data["criteria"]
    header = "".join(f"<th>{escape(c['title'][:24])}</th>" for c in crit)
    body = []
    for p in data["products"]:
        cells = "".join(f"<td>{escape(p['features'].get(c['id'], '—'))}</td>" for c in crit)
        body.append(f"<tr><th>{escape(p['label'])}</th>{cells}</tr>")
    return (
        f"<div class='matrix-scroll'><table class='matrix-table'><tr><th></th>{header}</tr>"
        f"{''.join(body)}</table></div>"
    )


def _section(tab: str, idx: int, title: str, body: str, extra: str = "") -> str:
    sid = f"{tab}-s{idx}"
    wrapped = mkt.wrap_section(body, f"{tab}-s{idx}", title)
    return f'<article id="{sid}" class="subsection">{wrapped}{extra}</article>'


def render_tab_pm() -> str:
    compare = render_compare_table()
    tco_svg = viz.render_tco_bars_svg(build_all_rows(load_offerings()))
    s1 = _section("pm", 1, "Ценность и roadmap", cnt.draft_pm_s1(), viz.render_feature_donut_svg())
    s2 = _section("pm", 2, "11 конкурентов", cnt.draft_pm_s2(), render_competitor_list())
    s3 = _section("pm", 3, "Сравнение тарифов", cnt.draft_pm_s3(), compare + tco_svg)
    s4 = _section("pm", 4, "Калькулятор поддержки", cnt.draft_pm_s4(), _calc_pm_html())
    return s1 + s2 + s3 + s4


def render_tab_tech() -> str:
    rows = build_all_rows(load_offerings())
    ru = rows[0].korus_at_competitor_ru if rows else 10_000
    s1 = _section("tech", 1, "Архитектура", cnt.draft_tech_s1(), viz.render_architecture_svg())
    s2 = _section("tech", 2, "Конкуренты (ops)", cnt.draft_tech_s2(), render_competitor_list())
    s3 = _section(
        "tech",
        3,
        "Критерии + sizing",
        cnt.draft_tech_s3(),
        render_feature_matrix() + viz.render_ram_bar_svg(ru),
    )
    s4 = _section("tech", 4, "Калькулятор мощностей", cnt.draft_tech_s4(), _calc_tech_html())
    return s1 + s2 + s3 + s4


def render_tab_sales() -> str:
    compare = render_compare_table()
    s1 = _section("sales", 1, "Value prop", cnt.draft_sales_s1())
    s2 = _section("sales", 2, "Конкуренты", cnt.draft_sales_s2(), render_competitor_list())
    s3 = _section(
        "sales",
        3,
        "TCO таблицы",
        cnt.draft_sales_s3(),
        compare + viz.render_tco_bars_svg(build_all_rows(load_offerings())),
    )
    s4 = _section("sales", 4, "Калькулятор TCO", cnt.draft_sales_s4(), _calc_sales_html())
    return s1 + s2 + s3 + s4


def render_tab_user() -> str:
    s1 = _section("user", 1, "Зачем мессенджер", cnt.draft_user_s1(), viz.render_user_timeline_svg())
    s2 = _section("user", 2, "Альтернативы", cnt.draft_user_s2())
    s3 = _section("user", 3, "Удобство", cnt.draft_user_s3())
    s4 = _section("user", 4, "Мастер и FAQ", cnt.draft_user_s4(), _user_wizard_html())
    return s1 + s2 + s3 + s4


def _calc_sales_html() -> str:
    return """
<div class="calc" data-calc="sales">
  <label>Рег. пользователей <input type="number" id="calc-sales-ru" value="7500" min="1"/></label>
  <button type="button" id="calc-sales-run">Рассчитать</button>
  <output id="calc-sales-out"></output>
</div>"""


def _calc_tech_html() -> str:
    return """
<div class="calc" data-calc="tech">
  <label>Рег. пользователей <input type="number" id="calc-tech-ru" value="500" min="1"/></label>
  <button type="button" id="calc-tech-run">Рассчитать</button>
  <output id="calc-tech-out"></output>
</div>"""


def _calc_pm_html() -> str:
    return """
<div class="calc" data-calc="pm">
  <label>Рег. пользователей <input type="number" id="calc-pm-ru" value="12000" min="1"/></label>
  <label>SLA <select id="calc-pm-sla"><option value="business">8×5</option><option value="24x7">24×7</option></select></label>
  <label><input type="checkbox" id="calc-pm-upd" checked/> Обновления</label>
  <button type="button" id="calc-pm-run">Рассчитать</button>
  <output id="calc-pm-out"></output>
</div>"""


def _user_wizard_html() -> str:
    return """
<div class="user-wizard">
  <h4>Мастер сценариев</h4>
  <select id="wizard-scenario"><option value="message">Написать коллеге</option><option value="search">Найти файл</option><option value="call">Позвонить</option></select>
  <ol id="wizard-steps"></ol>
  <h4>FAQ</h4>
  <details><summary>Чем не Telegram?</summary><p>Корпоративный аудит, хранение по политике компании, экспорт для комплаенса.</p></details>
  <details><summary>Нужно ли ставить приложение?</summary><p>Достаточно браузера; можно добавить на рабочий стол как приложение.</p></details>
  <h4>Тур</h4>
  <ol class="tour-steps"><li>Вход</li><li>Список чатов</li><li>Сообщение</li><li>Поиск</li></ol>
</div>"""


def deck_data_json() -> dict[str, Any]:
    profiles = [
        {
            "id": p.id,
            "ram_gb": p.ram_gb,
            "max_registered_users": p.max_registered_users,
            "monthly_rub": p.monthly_rub,
        }
        for p in sp.PROFILES
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
        "profiles": profiles,
        "offerings": offerings,
        "price_as_of": sp.PRICE_AS_OF,
        "offerings_max_as_of": max_as_of,
        "fte_base": {"base": 0.15, "divisor": 80000, "cap": 4.0},
        "scenarios": {
            "message": ["Откройте чат", "Выберите коллегу", "Напишите сообщение"],
            "search": ["Введите запрос", "Выберите результат", "Откройте файл"],
            "call": ["Откройте чат", "Нажмите звонок", "Разрешите микрофон"],
        },
    }


def _deck_css() -> str:
    return """
:root { --accent: #22c55e; --warn: #f59e0b; --bg: #fafafa; --text: #1e293b; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #0f172a; --text: #e2e8f0; }
}
* { box-sizing: border-box; }
body { font-family: system-ui, sans-serif; margin: 0; background: var(--bg); color: var(--text); line-height: 1.5; }
.block-0 { background: #fff7ed; border-bottom: 3px solid var(--warn); padding: 1rem 1.5rem; }
@media (prefers-color-scheme: dark) { .block-0 { background: #451a03; } }
.hero-warning h1 { margin-top: 0; }
.tab-bar { display: flex; flex-wrap: wrap; gap: .5rem; padding: .75rem 1rem; position: sticky; top: 0; background: var(--bg); border-bottom: 1px solid #cbd5e1; z-index: 10; }
.tab-bar button { min-height: 44px; padding: .5rem 1rem; border: 1px solid #94a3b8; background: #fff; cursor: pointer; border-radius: 6px; }
.tab-bar button[aria-selected="true"] { background: var(--accent); color: #fff; border-color: var(--accent); }
.tab-panel { display: none; padding: 1rem 1.5rem; max-width: 1100px; margin: 0 auto; }
.tab-panel.active { display: block; }
.sub-nav { display: flex; flex-wrap: wrap; gap: .5rem; margin-bottom: 1rem; }
.sub-nav a { font-size: .9rem; }
.compare-table, .feature-table, .matrix-table { width: 100%; border-collapse: collapse; font-size: .85rem; }
.compare-table th, .compare-table td, .feature-table th, .feature-table td { border: 1px solid #cbd5e1; padding: .35rem .5rem; }
.matrix-scroll { overflow-x: auto; }
.tag { font-size: .75rem; padding: .1rem .4rem; border-radius: 4px; }
.tag-done { background: #dcfce7; }
.tag-partial { background: #fef3c7; border: 1px dashed #f59e0b; }
.tag-planned { background: #e2e8f0; }
.tag-out { background: #fecaca; }
.chip-headroom { background: #dcfce7; color: #166534; font-size: .75rem; padding: .1rem .35rem; border-radius: 4px; margin-left: .25rem; }
.callout-info { background: #eff6ff; padding: .75rem; border-left: 4px solid #3b82f6; }
.deck-footer { text-align: center; font-size: .8rem; padding: 1.5rem; color: #64748b; border-top: 1px solid #e2e8f0; }
.calc output { display: block; margin-top: .5rem; font-weight: 600; }
@media (max-width: 640px) {
  .tab-bar { flex-direction: column; }
  .tab-bar button { width: 100%; }
  .block0-grid { display: block; }
  .compare-table { display: block; overflow-x: auto; white-space: nowrap; }
}
@media (max-width: 375px) {
  .tab-bar button { font-size: .85rem; padding: .45rem .65rem; }
  .tab-panel { padding: .75rem .65rem; }
  .sub-nav a { font-size: .8rem; }
  h2 { font-size: 1.05rem; }
}
@media print { .tab-bar, .sub-nav, .calc button { display: none; } .tab-panel { display: block !important; page-break-before: always; } }
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
  activate(['pm','tech','sales','user'].includes(hash)?hash:'pm');

  const data=JSON.parse(document.getElementById('deck-data').textContent);
  function pickProfile(ru){
    for(const p of data.profiles) if(ru<=p.max_registered_users) return p;
    return data.profiles[data.profiles.length-1];
  }
  function fmt(n){ return n.toString().replace(/\\B(?=(\\d{3})+(?!\\d))/g,' ')+' ₽'; }

  document.getElementById('calc-sales-run')?.addEventListener('click', ()=>{
    const ru=+document.getElementById('calc-sales-ru').value||1;
    const p=pickProfile(ru);
    document.getElementById('calc-sales-out').textContent=
      `Профиль ${p.id}: ${fmt(p.monthly_rub)}/мес, ${fmt(p.monthly_rub*12)}/год (~${(p.monthly_rub/ru).toFixed(1)} ₽/рег./мес)`;
  });
  document.getElementById('calc-tech-run')?.addEventListener('click', ()=>{
    const ru=+document.getElementById('calc-tech-ru').value||1;
    const p=pickProfile(ru);
    const nodes = p.id==='pilot'?1:(p.id==='standard'?3:6);
    document.getElementById('calc-tech-out').textContent=
      `RAM ${p.ram_gb} ГБ, узлов ~${nodes}, headroom до ${p.max_registered_users} рег.`;
  });
  document.getElementById('calc-pm-run')?.addEventListener('click', ()=>{
    const ru=+document.getElementById('calc-pm-ru').value||1;
    const sla=document.getElementById('calc-pm-sla').value;
    const upd=document.getElementById('calc-pm-upd').checked;
    let fte=0.15+ru/80000; if(fte>4) fte=4;
    if(upd) fte+= ru<5000?0.1:(ru<50000?0.3:0.8);
    if(sla==='24x7') fte*=2.5;
    const rub=Math.round(fte*180000);
    document.getElementById('calc-pm-out').textContent=`FTE ${fte.toFixed(2)}, ~${fmt(rub)}/мес`;
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
        f"{ps.DECK_VERSION} | {build_day} | PRICE_AS_OF {sp.PRICE_AS_OF} | "
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
