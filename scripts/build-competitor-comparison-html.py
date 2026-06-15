#!/usr/bin/env python3
"""Generate competitor_comparison.html — visual comparison presentation."""

from pathlib import Path
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from competitor_comparison_data import (  # noqa: E402
    PRICE_AS_OF,
    render_comparison_matrix_html,
    render_express_hardware_table_html,
    render_fig_infra_by_anchor_svg,
    render_fig_license_per_user_svg,
    render_fig_profile_floors_svg,
    render_fig_tco_s100k_svg,
    render_fig_tco_s10k_svg,
    render_korus_anchor_table_html,
    render_pros_cons_html,
)
from tz_product_pricing import PRICE_REGION, PRICE_VAT  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / "competitor_comparison.html"

CSS = """
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; line-height: 1.45;
           margin: 24px auto; color: #111; max-width: 980px; padding: 0 16px; }
    h1 { font-size: 24px; margin-top: 0; color: #1e3a5f; }
    h2 { font-size: 19px; border-top: 2px solid #6366f1; padding-top: 18px; margin-top: 32px; color: #1e3a5f; }
    h3 { font-size: 16px; margin-top: 20px; color: #374151; }
    h4 { font-size: 14px; margin: 8px 0 4px; color: #4b5563; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 14px; }
    th, td { border: 1px solid #d1d5db; padding: 8px 10px; vertical-align: top; }
    th { background: #eef2ff; text-align: left; font-weight: 600; }
    tr:nth-child(even) { background: #f9fafb; }
    .comment { color: #374151; }
    .req { font-weight: 600; color: #1e3a5f; }
    .note { background: #eff6ff; border: 1px solid #93c5fd; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .warn { background: #fff7ed; border: 1px solid #fdba74; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .cost-box { background: #f0fdf4; border: 1px solid #86efac; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .small { font-size: 12px; color: #6b7280; }
    .meta { color: #6b7280; font-size: 13px; margin-bottom: 24px; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; }
    .fig { margin: 20px 0; text-align: center; }
    .fig svg { max-width: 100%; height: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
    .fig-cap { font-size: 13px; color: #6b7280; margin-top: 8px; }
    .case { margin: 10px 0; padding: 12px 14px; background: #fafafa; border-left: 4px solid #6366f1; border-radius: 0 8px 8px 0; }
    .case h4 { margin: 0 0 6px; color: #111; }
    .case ul { margin: 6px 0; padding-left: 20px; }
    .money { font-weight: 600; color: #047857; }
    .toc a { text-decoration: none; color: #4338ca; }
    .toc a:hover { text-decoration: underline; }
    @media print { body { margin: 12px; max-width: none; } .note, .warn, .fig { break-inside: avoid; } }
"""


def main() -> None:
    html = f"""<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Сравнение Korus Messenger с конкурентами</title>
  <style>{CSS}</style>
</head>
<body>

<h1>Сравнение Korus Messenger с конкурентами</h1>
<div class="meta">
  <b>Версия:</b> 1.0 &nbsp;|&nbsp; <b>Дата ставок:</b> {PRICE_AS_OF} &nbsp;|&nbsp;
  <b>Регион:</b> {PRICE_REGION} &nbsp;|&nbsp; <b>НДС:</b> {PRICE_VAT}<br/>
  <b>Аудитория:</b> руководство, продажи, архитекторы, закупки<br/>
  <b>Методика (текст):</b> docs/COMPETITOR_COMPARISON_METHODOLOGY.md
</div>

<div class="note">
  <div class="req">Правила сравнения</div>
  <div class="comment">
    <b>Pilot (пробник)</b> — только POC до 10&nbsp;000 пользователей; <b>не участвует</b> в TCO-battle с eXpress Corporate.<br/>
    <b>Standard</b> — production от <b>10&nbsp;000</b> до <b>100&nbsp;000</b> RU. <b>Enterprise</b> — от <b>100&nbsp;000</b> до <b>1&nbsp;000&nbsp;000</b> RU.<br/>
    Ниже 10&nbsp;000 RU production-строку Korus Standard <b>не считаем</b>. Цифры infra — учебный шаблон, не оферта.
  </div>
</div>

<div class="toc small">
  <b>Содержание:</b>
  <a href="#s1">1. Профили и якоря</a> ·
  <a href="#s2">2. Korus: infra по якорям</a> ·
  <a href="#s3">3. TCO @10k и @100k</a> ·
  <a href="#s4">4. ₽/user</a> ·
  <a href="#s5">5. Матрица сравнения</a> ·
  <a href="#s6">6. eXpress: железо</a> ·
  <a href="#s7">7. Плюсы и минусы</a> ·
  <a href="#s8">8. Дисклеймеры</a>
</div>

<h2 id="s1">1. Профили и якоря расчёta</h2>
{render_fig_profile_floors_svg()}

<h2 id="s2">2. Korus Messenger: инфраструктура по якорям</h2>
{render_fig_infra_by_anchor_svg()}
{render_korus_anchor_table_html()}

<h2 id="s3">3. TCO: инфраструктура + лицензия</h2>
<p class="small">Korus: лицензия на ПО — отдельная строка коммерческого предложения (на диаграммах = 0).
  eXpress: 3&nbsp;000&nbsp;₽/пользователь/год (Corporate on-prem, публичный прайс).
  Пачка и VK — облачная подписка без своего железа.</p>
{render_fig_tco_s10k_svg()}
{render_fig_tco_s100k_svg()}

<h2 id="s4">4. Стоимость на одного пользователя в месяц (@10&nbsp;000)</h2>
{render_fig_license_per_user_svg()}

<h2 id="s5">5. Матрица сравнения (production)</h2>
<div class="cost-box">
{render_comparison_matrix_html()}
</div>

<h2 id="s6">6. eXpress: опубликованное железо и mapping на якоря Korus</h2>
{render_express_hardware_table_html()}

<h2 id="s7">7. Плюсы и минусы (кратко)</h2>
{render_pros_cons_html()}

<h2 id="s8">8. Дисклеймеры</h2>
<div class="warn">
  <ul class="comment">
    <li>Цифры infra Korus — ориентиры; formal load test до prod sign-off рекомендуется.</li>
    <li>Активные видеозвонки и массовые ВКС могут <b>удвоить</b> сеть и CPU (особенно eXpress).</li>
    <li>Ставки infra — усреднённые VDS/dedicated; подставьте прайс вашего провайдера в КП.</li>
    <li>eXpress infra @100k+ — оценка или индивидуальный проект вендора.</li>
    <li>Mattermost/Loop используют метрику <b>concurrent</b> — не смешивать с RU без пересчёта.</li>
    <li>Enterprise @500k/@1M — оценочный sizing Korus; уточняется на stage.</li>
  </ul>
</div>

<p class="small">Сборка: <code>python scripts/build-competitor-comparison-html.py</code></p>

</body>
</html>
"""
    OUT.write_text(html, encoding="utf-8")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
