#!/usr/bin/env python3
"""Generate competitor_comparison.html — visual comparison presentation."""

from pathlib import Path
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from competitor_comparison_data import (  # noqa: E402
    PRICE_AS_OF,
    render_comparison_matrix_html,
    render_express_full_table_html,
    render_feature_matrix_html,
    render_fig_express_infra_tiers_svg,
    render_fig_infra_by_anchor_svg,
    render_fig_license_per_user_svg,
    render_fig_license_share_svg,
    render_fig_profile_floors_svg,
    render_fig_ram_compare_svg,
    render_fig_tco_enterprise_svg,
    render_fig_tco_s100k_svg,
    render_fig_tco_s10k_svg,
    render_fig_legacy_infra_svg,
    render_fig_legacy_timeline_svg,
    render_legacy_feature_matrix_html,
    render_legacy_infra_table_html,
    render_legacy_migration_html,
    render_legacy_pros_cons_html,
    render_legacy_solutions_html,
    render_korus_anchor_table_html,
    render_nt_baseline_html,
    render_pilot_footnote_html,
    render_pricing_reference_html,
    render_pros_cons_html,
    render_reference_solutions_html,
    render_sources_html,
)
from tz_product_pricing import PRICE_REGION, PRICE_VAT  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / "competitor_comparison.html"

CSS = """
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; line-height: 1.45;
           margin: 24px auto; color: #111; max-width: 980px; padding: 0 16px; }
    h1 { font-size: 24px; margin-top: 0; color: #1e3a5f; }
    h2 { font-size: 19px; border-top: 2px solid #6366f1; padding-top: 18px; margin-top: 32px; color: #1e3a5f; }
    h3 { font-size: 16px; margin-top: 20px; color: #374151; }
    h4 { font-size: 14px; margin: 12px 0 6px; color: #4b5563; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 13px; }
    th, td { border: 1px solid #d1d5db; padding: 7px 9px; vertical-align: top; }
    th { background: #eef2ff; text-align: left; font-weight: 600; }
    tr:nth-child(even) { background: #f9fafb; }
    .comment { color: #374151; }
    .req { font-weight: 600; color: #1e3a5f; }
    .note { background: #eff6ff; border: 1px solid #93c5fd; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .warn { background: #fff7ed; border: 1px solid #fdba74; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .cost-box { background: #f0fdf4; border: 1px solid #86efac; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .legacy-box { background: #fafaf9; border: 1px solid #d6d3d1; padding: 14px; border-radius: 8px; margin: 14px 0; }
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
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 720px) { .grid-2 { grid-template-columns: 1fr; } }
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
  <b>Версия:</b> 1.4 &nbsp;|&nbsp; <b>Дата ставок:</b> {PRICE_AS_OF} &nbsp;|&nbsp;
  <b>Регион:</b> {PRICE_REGION} &nbsp;|&nbsp; <b>НДС:</b> {PRICE_VAT}<br/>
  <b>Методика:</b> docs/COMPETITOR_COMPARISON_METHODOLOGY.md
</div>

<div class="note">
  <div class="req">Промышленная матрица сравнения</div>
  <div class="comment">
    <b>Пробник (Pilot)</b> — вне TCO-сравнения. <b>Стандарт</b> от 10&nbsp;000 RU (S-10k, S-50k, S-100k).
    <b>Корпоративный</b> от 100&nbsp;000 RU (E-500k, E-1M). eXpress @100–1000 — справочно, ниже порога Korus.
  </div>
</div>

<div class="toc small">
  <a href="#s1">1 Профили</a> · <a href="#s2">2 Инфра Korus</a> · <a href="#s2nt">2.1 НТ QEMU</a> ·
  <a href="#s3">3 TCO</a> ·
  <a href="#s4">4 eXpress</a> · <a href="#s5">5 Функции</a> · <a href="#s5legacy">5.3 Устаревшие</a> ·
  <a href="#s6">6 Матрицы</a> ·
  <a href="#s7">7 Плюсы/минусы</a> · <a href="#s8">8 Источники</a>
</div>

{render_pilot_footnote_html()}

<h2 id="s1">1. Профили и якоря</h2>
{render_fig_profile_floors_svg()}

<h2 id="s2">2. Korus: инфраструктура</h2>
{render_fig_infra_by_anchor_svg()}
{render_korus_anchor_table_html()}

<h3 id="s2nt">2.1 Нагрузочное тестирование (QEMU, июнь 2026)</h3>
<div class="cost-box">
{render_nt_baseline_html()}
</div>

<h2 id="s3">3. TCO и стоимость на пользователя</h2>
<div class="grid-2">
  <div>{render_fig_tco_s10k_svg()}</div>
  <div>{render_fig_tco_s100k_svg()}</div>
</div>
{render_fig_tco_enterprise_svg()}
{render_fig_license_per_user_svg()}
{render_fig_license_share_svg()}
{render_pricing_reference_html()}

<h2 id="s4">4. eXpress — максимум публичных данных</h2>
{render_fig_express_infra_tiers_svg()}
{render_fig_ram_compare_svg()}
<div class="cost-box">
{render_express_full_table_html()}
</div>

<h2 id="s5">5. Функциональное сравнение и справочники</h2>
<h3>5.1 Матрица возможностей</h3>
{render_feature_matrix_html()}
<h3>5.2 Другие решения (справочно)</h3>
{render_reference_solutions_html()}

<h3 id="s5legacy">5.3 Устаревшие платформы (Jabber / XMPP и аналоги)</h3>
<div class="legacy-box">
  <div class="comment">
    Устаревшие серверы <b>не входят</b> в промышленную TCO-матрицу (§6) — отдельный контур для заказчиков
    с действующим Jabber, Sametime, Lync/Skype for Business. Сравнение: функции, только инфра, сценарии миграции.
  </div>
</div>
{render_fig_legacy_timeline_svg()}
{render_legacy_solutions_html()}
{render_fig_legacy_infra_svg()}
{render_legacy_infra_table_html()}
<h4>5.3.1 Функции: Korus и устаревшие платформы</h4>
{render_legacy_feature_matrix_html()}
<h4>5.3.2 Миграция и плюсы/минусы</h4>
{render_legacy_migration_html()}
{render_legacy_pros_cons_html()}

<h2 id="s6">6. Матрицы TCO по всем якорям Korus</h2>
<div class="cost-box">
{render_comparison_matrix_html()}
</div>

<h2 id="s7">7. Плюсы и минусы</h2>
{render_pros_cons_html()}

<h2 id="s8">8. Источники и дисклеймеры</h2>
{render_sources_html()}
<div class="warn">
  <ul class="comment">
    <li>Цифры инфраструктуры — ориентиры; нагрузочное тестирование до промышленной приёмки рекомендуется.</li>
    <li>ВКС и массовые звонки могут удвоить CPU/сеть (особенно eXpress Media).</li>
    <li>eXpress @10k/100k infra — модельная оценка, не оферта вендора.</li>
    <li>Mattermost: concurrent ≠ RU Korus/eXpress.</li>
    <li>Устаревший XMPP — модель HA-кластера; реальные контуры часто на одном узле.</li>
    <li>Sametime/Lync — не «только чат»; миграция часто параллельна с Teams/UC.</li>
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
