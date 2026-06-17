#!/usr/bin/env python3
"""Generate competitor_comparison.html (full) and competitor_comparison_brief.html (sales one-pager)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from tz_product_plugin_sizing import render_plugin_sizing_table_html  # noqa: E402

from competitor_comparison_data import (  # noqa: E402
    PRICE_AS_OF,
    _section_lead,
    render_audience_nav_html,
    render_brief_disclaimers_html,
    render_battle_card_html,
    render_comparison_matrix_html,
    render_cta_footer_html,
    render_decision_tree_html,
    render_deployment_models_html,
    render_product_scenario_matrix_html,
    render_elevator_pitch_html,
    render_email_snippet_html,
    render_executive_summary_html,
    render_express_full_table_html,
    render_feature_heatmap_svg,
    render_feature_matrix_html,
    render_fig_express_infra_tiers_svg,
    render_fig_infra_by_anchor_svg,
    render_fig_license_per_user_svg,
    render_fig_license_share_svg,
    render_fig_onprem_radar_svg,
    render_fig_profile_floors_svg,
    render_fig_ram_compare_svg,
    render_fig_tco_enterprise_svg,
    render_fig_tco_s100k_svg,
    render_fig_tco_s10k_svg,
    render_fig_tco_s50k_svg,
    render_fig_tco_tier_c_svg,
    render_fig_tier_c_radar_svg,
    render_enterprise_saas_callout_html,
    render_fig_legacy_infra_svg,
    render_fig_legacy_timeline_svg,
    render_full_disclaimers_html,
    render_glossary_html,
    render_hero_html,
    render_korus_positioning_html,
    render_objections_faq_html,
    render_part_divider_html,
    render_reading_guide_html,
    render_segment_cards_html,
    render_segment_page_body,
    render_segment_links_html,
    SEGMENT_SPECS,
    render_value_pillars_html,
    render_legacy_feature_matrix_html,
    render_legacy_infra_table_html,
    render_legacy_migration_html,
    render_legacy_pros_cons_html,
    render_legacy_solutions_html,
    render_korus_anchor_table_html,
    render_nt_baseline_html,
    render_pilot_footnote_html,
    render_pricing_reference_html,
    render_pros_cons_brief_html,
    render_pros_cons_html,
    render_reference_solutions_html,
    render_sources_html,
    render_tier_b_tco_html,
    render_tier_c_market_html,
    render_tier_overview_html,
    render_talk_track_html,
)
from tz_product_pricing import PRICE_REGION, PRICE_VAT  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[1]
OUT_FULL = REPO_ROOT / "competitor_comparison.html"
OUT_BRIEF = REPO_ROOT / "competitor_comparison_brief.html"
OUT_TALKTRACK = REPO_ROOT / "competitor_comparison_talktrack.html"
VERSION = "3.1"

CSS = """
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; line-height: 1.45;
           margin: 24px auto; color: #111; max-width: 1240px; padding: 0 20px; }
    h1 { font-size: 26px; margin-top: 0; color: #1e3a5f; }
    h2 { font-size: 20px; border-top: 2px solid #6366f1; padding-top: 18px; margin-top: 32px; color: #1e3a5f; }
    h3 { font-size: 17px; margin-top: 20px; color: #374151; }
    h4 { font-size: 15px; margin: 12px 0 6px; color: #4b5563; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 14px; }
    th, td { border: 1px solid #d1d5db; padding: 8px 10px; vertical-align: top; }
    th { background: #eef2ff; text-align: left; font-weight: 600; }
    tr:nth-child(even) { background: #f9fafb; }
    .matrix-wide { font-size: 12px; min-width: 960px; }
    .matrix-scroll { overflow-x: auto; margin: 12px 0; border: 1px solid #e5e7eb; border-radius: 8px; }
    .comment { color: #374151; }
    .req { font-weight: 600; color: #1e3a5f; }
    .note { background: #eff6ff; border: 1px solid #93c5fd; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .warn { background: #fff7ed; border: 1px solid #fdba74; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .cost-box { background: #f0fdf4; border: 1px solid #86efac; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .legacy-box { background: #fafaf9; border: 1px solid #d6d3d1; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .small { font-size: 13px; color: #6b7280; }
    .meta { color: #6b7280; font-size: 14px; margin-bottom: 24px; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; }
    .meta-link { display: inline-block; margin-top: 6px; }
    .meta-link a { color: #4338ca; font-weight: 600; }
    .fig { margin: 24px 0; text-align: center; }
    .fig svg { display: block; margin: 0 auto; max-width: 100%; height: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
    .fig-wide svg { min-width: min(100%, 880px); }
    .fig-scroll { overflow-x: auto; margin: 20px 0; -webkit-overflow-scrolling: touch; border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 4px; background: #fafafa; }
    .fig-scroll svg { min-width: 900px; }
    .fig-cap { font-size: 14px; color: #6b7280; margin-top: 10px; }
    .fig-stack { display: flex; flex-direction: column; gap: 20px; }
    .case { margin: 10px 0; padding: 12px 14px; background: #fafafa; border-left: 4px solid #6366f1; border-radius: 0 8px 8px 0; }
    .case h4 { margin: 0 0 6px; color: #111; }
    .case ul { margin: 6px 0; padding-left: 20px; }
    .money { font-weight: 600; color: #047857; }
    .toc a { text-decoration: none; color: #4338ca; }
    .toc a:hover { text-decoration: underline; }
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; align-items: start; }
    .grid-2 .fig svg { width: 100%; }
    .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; align-items: start; }
    .persona-extracts { margin: 16px 0; }
    .persona-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 14px; }
    .persona-card .req { font-size: 13px; margin-bottom: 6px; }
    .talk-track { margin: 16px 0; }
    .talk-slot { margin: 10px 0; padding: 8px 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; }
    .talk-slot summary { cursor: pointer; font-weight: 600; }
    .scenario { padding: 12px 14px; background: #fafafa; border-radius: 8px; border: 1px solid #e5e7eb; }
    .scenario h4 { margin: 0 0 8px; font-size: 15px; color: #1e3a5f; }
    .scenario-korus { background: #f0fdf4; border-color: #86efac; }
    .scenario-rec { margin: 10px 0 0; font-size: 13px; }
    .rec-korus { color: #047857; font-weight: 700; }
    .hero-subtitle { font-size: 16px; margin: -8px 0 20px; color: #4b5563; }
    .hero { background: linear-gradient(135deg, #1e3a5f 0%, #312e81 100%); color: #fff;
            padding: 28px 32px; border-radius: 12px; margin-bottom: 24px; }
    .hero-lead { font-size: 17px; line-height: 1.5; margin: 0 0 20px; max-width: 920px; }
    .hero-note { margin: 16px 0 0; opacity: 0.85; }
    .hero-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .stat-card { background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.2);
                  border-radius: 10px; padding: 16px; text-align: center; }
    .stat-korus { background: rgba(34,197,94,0.25); border-color: #86efac; }
    .stat-value { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
    .stat-label { font-size: 13px; line-height: 1.35; opacity: 0.95; }
    .audience-nav { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 20px 0 28px; }
    .audience-card { display: block; padding: 14px; background: #fff; border: 1px solid #c7d2fe;
                     border-radius: 8px; text-decoration: none; color: inherit; transition: box-shadow .15s; }
    .audience-card:hover { box-shadow: 0 4px 12px rgba(99,102,241,.15); border-color: #6366f1; }
    .audience-role { display: block; font-weight: 700; color: #4338ca; font-size: 14px; margin-bottom: 4px; }
    .audience-hint { display: block; font-size: 12px; color: #6b7280; line-height: 1.35; }
    .positioning { background: #f8fafc; border: 1px solid #cbd5e1; padding: 18px 20px; border-radius: 10px; margin: 20px 0; }
    .positioning-tagline { margin: 14px 0 0; padding-top: 12px; border-top: 1px dashed #cbd5e1; }
    .decision-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
    .decision-card { background: #fff; border: 1px solid #d1d5db; border-radius: 8px; padding: 14px; }
    .decision-card-muted { background: #f9fafb; }
    .decision-q { font-weight: 700; color: #1e3a5f; margin-bottom: 8px; font-size: 14px; }
    .decision-a { font-size: 13px; color: #374151; }
    .decision-a ul { margin: 8px 0 0; padding-left: 18px; }
    .cta-box { background: #eef2ff; border: 2px solid #6366f1; padding: 18px 22px; border-radius: 10px; margin: 28px 0; }
    .row-korus { background: #f0fdf4 !important; }
    .row-korus td:first-child + td { font-weight: 700; color: #047857; }
    .case-korus { background: #f0fdf4; border-left-color: #22c55e; }
    .meta-tech { margin-top: 8px; }
    .tier-a { background: #dcfce7; color: #166534; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
    .tier-b { background: #e0e7ff; color: #3730a3; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
    .tier-c { background: #fef3c7; color: #92400e; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
    details.express-details { margin: 12px 0; padding: 8px 12px; border: 1px solid #e5e7eb; border-radius: 8px; }
    details.express-details summary { cursor: pointer; font-weight: 600; color: #1e3a5f; }
    .brief-badge { display: inline-block; background: #6366f1; color: #fff; font-size: 12px; font-weight: 600;
                   padding: 3px 10px; border-radius: 999px; margin-left: 8px; vertical-align: middle; }
    .elevator { background: #fafafa; border-left: 4px solid #6366f1; padding: 14px 18px; margin: 0 0 20px; border-radius: 0 8px 8px 0; }
    .elevator-text { margin: 8px 0 0; font-size: 15px; line-height: 1.55; }
    .pillars { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin: 20px 0 28px; }
    .pillar { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px; }
    .pillar-korus { border-top: 3px solid #22c55e; }
    .pillar-title { font-weight: 700; color: #1e3a5f; margin-bottom: 8px; font-size: 15px; }
    .battle-card { margin: 24px 0; }
    .battle-table th.col-korus, .battle-table td.col-korus { background: #f0fdf4; }
    .battle-table .col-korus { border-left: 2px solid #22c55e; }
    .faq { margin: 24px 0; }
    .faq-item { margin: 8px 0; padding: 10px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
    .faq-item summary { cursor: pointer; font-weight: 600; color: #1e3a5f; }
    .faq-item p { margin: 10px 0 0; }
    .segments { margin: 24px 0; }
    .segment-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
    .segment-card { background: #fff; border: 1px solid #d1d5db; border-radius: 10px; padding: 16px; }
    .segment-label { font-weight: 700; color: #4338ca; margin-bottom: 10px; font-size: 14px; }
    .email-snippet { margin: 20px 0; border: 1px dashed #c7d2fe; border-radius: 8px; padding: 10px 14px; }
    .email-body { white-space: pre-wrap; font-family: inherit; font-size: 13px; line-height: 1.5;
                  background: #f9fafb; padding: 14px; border-radius: 6px; margin: 10px 0 0; }
    .part-divider { margin: 36px 0 24px; padding: 18px 22px;
                     background: linear-gradient(90deg, #eef2ff 0%, #fff 100%);
                     border-left: 5px solid #6366f1; border-radius: 0 10px 10px 0; }
    .part-badge { font-size: 12px; font-weight: 700; color: #6366f1; text-transform: uppercase; letter-spacing: .04em; }
    .part-title { font-size: 22px; font-weight: 700; color: #1e3a5f; margin: 6px 0; }
    .part-subtitle { margin: 0; font-size: 14px; }
    .section-lead { font-size: 15px; margin: -8px 0 16px; max-width: 920px; }
    .reading-guide { margin: 16px 0 24px; }
    .glossary { margin: 16px 0 24px; }
    .glossary table { font-size: 13px; }
    .segment-doc-badge { display: inline-block; background: #4338ca; color: #fff; font-size: 11px; font-weight: 700;
                          padding: 4px 10px; border-radius: 999px; margin-left: 10px; vertical-align: middle; }
    .segment-links a { font-weight: 600; }
    @media (max-width: 900px) {
      .grid-2 { grid-template-columns: 1fr; }
      .grid-3 { grid-template-columns: 1fr; }
      .hero-stats { grid-template-columns: 1fr; }
      .audience-nav { grid-template-columns: 1fr 1fr; }
      .decision-cards { grid-template-columns: 1fr; }
      .pillars, .segment-grid { grid-template-columns: 1fr; }
    }
    @media print {
      body { margin: 10mm; max-width: none; font-size: 10.5pt; }
      .toc, .audience-nav, .meta-tech, .technical-only { display: none !important; }
      h1, h2, h3 { break-after: avoid; }
      table { font-size: 9pt; }
      .note, .warn, .fig, .hero, .positioning, .cta-box, details.express-details { break-inside: avoid; }
      .fig-scroll { overflow: visible; border: none; background: transparent; }
      .hero { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    }
"""


def _meta_block(*, brief: bool) -> str:
    kind = "краткая версия для встречи / PDF" if brief else "полная версия"
    sibling = (
        '<span class="meta-link">Полное сравнение: <a href="competitor_comparison.html">competitor_comparison.html</a></span>'
        if brief
        else '<span class="meta-link">One-pager для PDF: <a href="competitor_comparison_brief.html">competitor_comparison_brief.html</a></span>'
    )
    badge = '<span class="brief-badge">ONE-PAGER</span>' if brief else ""
    return f"""
<div class="meta">
  <b>Актуальность ставок:</b> {PRICE_AS_OF} &nbsp;|&nbsp;
  <b>Регион:</b> {PRICE_REGION} &nbsp;|&nbsp; <b>НДС:</b> {PRICE_VAT} &nbsp;|&nbsp;
  <b>Версия:</b> {VERSION} ({kind}){badge}<br/>
  {sibling}
</div>"""


def _wrap_page(*, title: str, body: str, brief: bool) -> str:
    body_cls = ' class="brief-doc"' if brief else ""
    return f"""<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>{title}</title>
  <style>{CSS}</style>
</head>
<body{body_cls}>

{body}

</body>
</html>"""


def build_talktrack_html() -> str:
    body = f"""
<h1>Talk track — Korus vs рынок</h1>
<p class="hero-subtitle comment">Сценарии встречи 5 / 15 / 45 мин · spec 012 Phase C</p>
{_meta_block(brief=False)}
{render_reading_guide_html()}
{render_talk_track_html()}
{render_segment_links_html()}
<p class="small comment">Полная презентация: <a href="competitor_comparison.html">competitor_comparison.html</a> ·
<a href="competitor_comparison_brief.html">one-pager</a></p>
"""
    return _wrap_page(title="Korus Messenger — talk track (5/15/45 мин)", body=body, brief=False)


def build_full_html() -> str:
    body = f"""
<h1>Korus Messenger vs рынок корпоративных мессенджеров</h1>
<p class="hero-subtitle comment">Сравнительная презентация для продаж, закупки и ИБ · единая методика TCO и функций</p>

{render_hero_html()}
{render_elevator_pitch_html()}
{render_value_pillars_html()}
{_meta_block(brief=False)}

{render_reading_guide_html()}
{render_glossary_html()}
{render_segment_links_html()}

{render_audience_nav_html()}

<div class="toc small nav">
  <a href="#part-I">Часть I</a> · <a href="#summary">Сценарии</a> · <a href="#positioning">Позиция</a> ·
  <a href="#battle">Battle card</a> · <a href="#faq">FAQ</a> · <a href="#segments">Сегменты</a> ·
  <a href="#part-II">Часть II</a> · <a href="#s3">Экономика</a> · <a href="#s5">Функции</a> ·
  <a href="#s6">Матрицы</a> · <a href="#part-III">Часть III</a> · <a href="#s8">Источники</a>
</div>

{render_part_divider_html("I", "Продажи и позиционирование", "Сценарии, battle card, FAQ — достаточно для первой встречи и email.")}

{render_executive_summary_html()}
{render_korus_positioning_html()}
{render_battle_card_html()}
{render_decision_tree_html()}
{render_product_scenario_matrix_html()}
{render_objections_faq_html()}
{render_segment_cards_html()}

{render_part_divider_html("II", "Обоснование КП и тендера", "TCO, функции, матрицы по якорям — для закупки, CFO и архитектора.")}

<h2 id="s0">Карта рынка: 11 решений в трёх уровнях сравнения</h2>
{_section_lead("11 продуктов в уровнях A (полный TCO), B (open-source в контуре), C (российский рынок).")}
{render_tier_overview_html()}

<div class="note">
  <div class="req">Промышленная матрица сравнения</div>
  <div class="comment">
    <b>Пробник</b> — вне TCO-сравнения. <b>Стандарт</b> от 10&nbsp;000 рег. пользов. (S-10k, S-50k, S-100k).
    <b>Корпоративный</b> от 100&nbsp;000 рег. пользов. (E-500k, E-1M). eXpress при 100–1000 рег. пользов. — справочно.
    <b>Облачные конкуренты (Пачка, VK)</b> не участвуют в Enterprise TCO @500k+ — нет развёртывания в контуре.
  </div>
</div>

<div class="toc small nav technical-only">
  <a href="#s1">Профили</a> · <a href="#s2">Инфра Korus</a> · <a href="#s2nt">НТ</a> ·
  <a href="#s3b">TCO B/C</a> · <a href="#s4">eXpress</a> · <a href="#s5legacy">Legacy</a> · <a href="#s6">Матрицы</a>
</div>

{render_pilot_footnote_html()}

<h2 id="s1">Профили Korus и якоря масштаба</h2>
{_section_lead("Профили нагрузки и якоря S-10k … E-1M — основа честного сравнения по масштабу.")}
{render_fig_profile_floors_svg()}

<h2 id="s2">Инфраструктура Korus по якорям</h2>
{_section_lead(f"Оценка железа по ставкам {PRICE_AS_OF}; не оферта облачного провайдера.")}
{render_fig_infra_by_anchor_svg()}
{render_korus_anchor_table_html()}

<h2 id="bot-sizing">Sizing узла ботов и плагинов</h2>
{_section_lead("Модель «1 экземпляр = 1 бот»; узел интеграций отдельно от сервера чатов. Сравнение с eXpress Bot cluster @1k.")}
{render_plugin_sizing_table_html()}

<h3 id="s2nt">Нагрузочные испытания (QEMU, июнь 2026)</h3>
{_section_lead("Замеры на стенде QEMU; целевые KPI якоря S-10k — для аргументации архитектору.")}
<div class="cost-box">
{render_nt_baseline_html()}
</div>

<h2 id="s3">Стоимость владения (уровень A): Korus vs eXpress vs облако</h2>
{_section_lead("Уровень A: Korus, eXpress, облако (Пачка/VK) на одних якорях рег. пользов.")}
<div class="grid-2">
  <div>{render_fig_tco_s10k_svg()}</div>
  <div>{render_fig_tco_s50k_svg()}</div>
</div>
<div class="grid-2">
  <div>{render_fig_tco_s100k_svg()}</div>
</div>
{render_enterprise_saas_callout_html()}
{render_fig_tco_enterprise_svg()}
<p class="small comment">График «Корпоративный»: Korus и eXpress в контуре. Облако (Пачка, VK) на E-500k/E-1M не применимо без облачного контура.</p>
{render_fig_license_per_user_svg()}
{render_fig_license_share_svg()}
{render_pricing_reference_html()}

<h3 id="s3b">Альтернативы уровней B и C</h3>
{render_tier_b_tco_html()}
{render_tier_c_market_html()}
{render_fig_tco_tier_c_svg()}
{render_deployment_models_html()}

<h2 id="s5">Функции и возможности</h2>
{_section_lead("18 критериев + heatmap: функциональный gap analysis для RFP и ИБ.")}
<h3>Обзор: тепловая карта и профиль «в контуре»</h3>
{render_feature_heatmap_svg()}
{render_fig_onprem_radar_svg()}
{render_fig_tier_c_radar_svg()}
<h3>Детальная матрица (18 критериев)</h3>
{render_feature_matrix_html()}
<h3>Справочно: open-source и EE (уровень B)</h3>
{render_reference_solutions_html()}

<h2 id="s6">Сводные матрицы TCO по якорям</h2>
{_section_lead("Сводные таблицы TCO по всем якорям — для вставки в Excel и КП.")}
<div class="cost-box">
{render_comparison_matrix_html()}
</div>

<h2 id="s7">Плюсы и минусы по продуктам</h2>
{_section_lead("Аргументы «за/против» по каждому продукту для пресейла и защиты тендера.")}
{render_pros_cons_html()}

{render_part_divider_html("III", "Справочник и legacy", "eXpress, миграция с XMPP, источники — для пресейла и аналитика.")}

<h2 id="s4">eXpress: публичные данные для сравнения</h2>
{_section_lead("Публичные материалы eXpress: infra tiers, RAM, типовые конфигурации.")}
{render_fig_express_infra_tiers_svg()}
{render_fig_ram_compare_svg()}
<div class="cost-box">
{render_express_full_table_html()}
</div>

<h3 id="s5legacy">Миграция с устаревших платформ (Jabber / XMPP)</h3>
{_section_lead("Миграция с Jabber/Sametime/Lync: справочно, вне production-матрицы уровня A.")}
<div class="legacy-box">
  <div class="comment">
    Устаревшие серверы <b>не входят</b> в промышленную TCO-матрицу уровня A — контур миграции.
  </div>
</div>
{render_fig_legacy_timeline_svg()}
{render_legacy_solutions_html()}
{render_fig_legacy_infra_svg()}
{render_legacy_infra_table_html()}
<h4>Функции: Korus и устаревшие платформы</h4>
{render_legacy_feature_matrix_html()}
<h4>Миграция и плюсы/минусы legacy</h4>
{render_legacy_migration_html()}
{render_legacy_pros_cons_html()}

{render_cta_footer_html()}

<h2 id="s8">Источники и ограничения методики</h2>
{render_sources_html()}
{render_full_disclaimers_html()}

<details class="meta-tech small">
  <summary>Техническая информация для аналитиков</summary>
  <p>Методика: docs/COMPETITOR_COMPARISON_METHODOLOGY.md (v1.6) · Охват: A (4) + B (4) + C (3) + legacy (7) · Talk track: competitor_comparison_talktrack.html<br/>
  Сборка: <code>python scripts/build-competitor-comparison-html.py</code></p>
</details>
"""
    return _wrap_page(
        title="Korus Messenger vs рынок — полное сравнение",
        body=body,
        brief=False,
    )


def _meta_block_segment(slug: str) -> str:
    spec = SEGMENT_SPECS[slug]
    return f"""
<div class="meta">
  <b>Актуальность ставок:</b> {PRICE_AS_OF} &nbsp;|&nbsp;
  <b>Регион:</b> {PRICE_REGION} &nbsp;|&nbsp; <b>НДС:</b> {PRICE_VAT} &nbsp;|&nbsp;
  <b>Версия:</b> {VERSION} (сегмент: {spec["badge"]})<br/>
  <span class="meta-link">Полное: <a href="competitor_comparison.html">competitor_comparison.html</a> ·
  <a href="competitor_comparison_brief.html">one-pager</a></span>
</div>"""


def build_segment_html(slug: str) -> str:
    spec = SEGMENT_SPECS[slug]
    body = f"""
{_meta_block_segment(slug)}
{render_segment_page_body(slug)}
"""
    return _wrap_page(
        title=spec["title"],
        body=body,
        brief=True,
    )


def build_brief_html() -> str:
    body = f"""
<h1>Korus Messenger vs рынок<span class="brief-badge">ONE-PAGER</span></h1>
<p class="hero-subtitle comment">Краткая версия для первой встречи, email и печати в PDF · ~6–8 страниц</p>

{render_hero_html()}
{render_elevator_pitch_html()}
{render_value_pillars_html()}
{_meta_block(brief=True)}

{render_reading_guide_html()}
{render_glossary_html()}

{render_executive_summary_html()}
{render_korus_positioning_html()}
{render_battle_card_html()}
{render_decision_tree_html()}
{render_product_scenario_matrix_html()}
{render_objections_faq_html()}
{render_segment_cards_html()}

<h2 id="s0">Карта рынка (кратко)</h2>
{_section_lead("Сжатый обзор 11 продуктов; детали — в полной версии.")}
{render_tier_overview_html()}

<h2 id="s3">Экономика @10 тыс. рег. пользов.</h2>
{_section_lead("Ключевой якорь для первой встречи: TCO и доля лицензии.")}
<div class="grid-2">
  <div>{render_fig_tco_s10k_svg()}</div>
  <div>{render_fig_license_share_svg()}</div>
</div>
{render_fig_license_per_user_svg()}

<h2 id="s5">Профиль «в контуре»</h2>
{_section_lead("Radar @10k — быстрый функциональный срез для ИБ и архитектора.")}
{render_fig_onprem_radar_svg()}

<h2 id="s7">Korus vs eXpress</h2>
{render_pros_cons_brief_html()}

{render_email_snippet_html()}
{render_cta_footer_html()}

<h2 id="s8">Оговорки</h2>
{render_brief_disclaimers_html()}

<p class="small comment">Детали: 18 критериев, legacy, НТ, матрицы @50k/100k/500k — в
<a href="competitor_comparison.html">competitor_comparison.html</a>.</p>
"""
    return _wrap_page(
        title="Korus Messenger — one-pager для продаж",
        body=body,
        brief=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Build competitor comparison HTML presentations")
    parser.add_argument("--full-only", action="store_true", help="Only competitor_comparison.html")
    parser.add_argument("--brief-only", action="store_true", help="Only competitor_comparison_brief.html")
    parser.add_argument(
        "--talktrack-only",
        action="store_true",
        help="Only competitor_comparison_talktrack.html",
    )
    parser.add_argument(
        "--segments-only",
        action="store_true",
        help="Only segment one-pagers (bank, industry, cloud)",
    )
    parser.add_argument(
        "--segment",
        choices=tuple(SEGMENT_SPECS.keys()),
        help="Build a single segment one-pager",
    )
    args = parser.parse_args()

    build_full = build_brief = build_segments = build_talktrack = True
    if args.full_only:
        build_brief = build_segments = build_talktrack = False
    if args.brief_only:
        build_full = build_segments = build_talktrack = False
    if args.talktrack_only:
        build_full = build_brief = build_segments = False
    if args.segments_only:
        build_full = build_brief = build_talktrack = False
    if args.segment:
        build_full = build_brief = build_talktrack = False
        build_segments = True

    if build_full:
        OUT_FULL.write_text(build_full_html(), encoding="utf-8")
        print(f"Wrote {OUT_FULL}")
    if build_brief:
        OUT_BRIEF.write_text(build_brief_html(), encoding="utf-8")
        print(f"Wrote {OUT_BRIEF}")
    if build_segments:
        slugs = (args.segment,) if args.segment else tuple(SEGMENT_SPECS.keys())
        for slug in slugs:
            out = REPO_ROOT / SEGMENT_SPECS[slug]["filename"]
            out.write_text(build_segment_html(slug), encoding="utf-8")
            print(f"Wrote {out}")
    if build_talktrack:
        OUT_TALKTRACK.write_text(build_talktrack_html(), encoding="utf-8")
        print(f"Wrote {OUT_TALKTRACK}")


if __name__ == "__main__":
    main()
