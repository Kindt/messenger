"""Inline SVG visuals — data-driven charts for the deck."""

from __future__ import annotations

from html import escape

from scripts.presentation import product_status as ps
from scripts.presentation import sizing_pricing as sp
from scripts.presentation.compare_engine import CompareRow, build_all_rows
from scripts.presentation.data_loader import load_offerings


def render_feature_donut_svg() -> str:
    counts = {s: len(ps.features_by_status(s)) for s in ("done", "partial", "planned", "out")}
    total = sum(counts.values()) or 1
    colors = {"done": "#22c55e", "partial": "#f59e0b", "planned": "#94a3b8", "out": "#64748b"}
    cx, cy, r = 80, 80, 60
    start = -90
    arcs = []
    legend = []
    for status, n in counts.items():
        if n == 0:
            continue
        angle = 360 * n / total
        end = start + angle
        import math

        x1 = cx + r * math.cos(math.radians(start))
        y1 = cy + r * math.sin(math.radians(start))
        x2 = cx + r * math.cos(math.radians(end))
        y2 = cy + r * math.sin(math.radians(end))
        large = 1 if angle > 180 else 0
        arcs.append(
            f'<path d="M{cx},{cy} L{x1:.1f},{y1:.1f} A{r},{r} 0 {large},1 {x2:.1f},{y2:.1f} Z" '
            f'fill="{colors[status]}"/>'
        )
        label = ps.STATUS_TAG[status][1]
        legend.append(f'<text x="170" y="{20 + 18 * len(legend)}" font-size="11">{label}: {n}</text>')
        start = end
    return f"""<svg viewBox="0 0 280 160" width="280" height="160" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Состояние функций">
  <text x="80" y="12" text-anchor="middle" font-size="12" font-weight="bold">Функции продукта</text>
  {''.join(arcs)}
  {''.join(legend)}
</svg>"""


def render_tco_bars_svg(rows: list[CompareRow], limit: int = 6) -> str:
    tco_rows = [r for r in rows if r.competitor_total_yearly_rub is not None][:limit]
    if not tco_rows:
        return '<p class="small">Нет строк с публичным TCO для диаграммы.</p>'
    max_v = max(max(r.competitor_total_yearly_rub or 0, r.korus_infra_yearly_rub) for r in tco_rows)
    bars = []
    y = 30
    for i, row in enumerate(tco_rows):
        label = escape(row.offering["label"][:20])
        comp = row.competitor_total_yearly_rub or 0
        kor = row.korus_infra_yearly_rub
        w_comp = int(400 * comp / max_v) if max_v else 0
        w_kor = int(400 * kor / max_v) if max_v else 0
        bars.append(
            f'<text x="0" y="{y}" font-size="10">{label}</text>'
            f'<rect x="120" y="{y-10}" width="{w_comp}" height="8" fill="#6366f1"/>'
            f'<rect x="120" y="{y+2}" width="{w_kor}" height="8" fill="#22c55e"/>'
        )
        y += 28
    h = y + 20
    return f"""<svg viewBox="0 0 540 {h}" width="540" height="{h}" xmlns="http://www.w3.org/2000/svg">
  <text x="120" y="16" font-size="11">■ конкурент  ■ Korus infra @ их RU</text>
  {''.join(bars)}
</svg>"""


def render_ram_bar_svg(ru: int) -> str:
    prof = sp.pick_profile(ru)
    max_ram = sp.PROFILES[-1].ram_gb
    w = int(300 * prof.ram_gb / max_ram)
    head = ""
    hr = sp.headroom_ru(prof, ru)
    if hr and hr > ru:
        head = f'<text x="320" y="48" font-size="10" fill="#22c55e">headroom до {hr:,}</text>'.replace(",", " ")
    return f"""<svg viewBox="0 0 480 70" width="480" height="70" xmlns="http://www.w3.org/2000/svg">
  <text x="0" y="14" font-size="11">RAM @ {ru:,} рег.: {prof.ram_gb} ГБ (профиль {escape(prof.label)})</text>
  <rect x="0" y="24" width="300" height="16" fill="#e5e7eb"/>
  <rect x="0" y="24" width="{w}" height="16" fill="#22c55e"/>
  {head}
  <text x="0" y="62" font-size="9" fill="#64748b">оценка по профилю infra, не stage load test</text>
</svg>"""


def render_architecture_svg() -> str:
    return """<svg viewBox="0 0 520 200" width="520" height="200" xmlns="http://www.w3.org/2000/svg">
  <text x="260" y="18" text-anchor="middle" font-size="12" font-weight="bold">Логическая схема прототипа</text>
  <rect x="20" y="40" width="120" height="40" rx="4" fill="#dbeafe"/><text x="80" y="65" text-anchor="middle" font-size="10">Web UI</text>
  <rect x="180" y="40" width="120" height="40" rx="4" fill="#bbf7d0"/><text x="240" y="65" text-anchor="middle" font-size="10">core-api</text>
  <rect x="340" y="40" width="120" height="40" rx="4" fill="#fde68a"/><text x="400" y="65" text-anchor="middle" font-size="10">workers</text>
  <rect x="100" y="110" width="100" height="36" rx="4" fill="#e9d5ff"/><text x="150" y="132" text-anchor="middle" font-size="9">PostgreSQL</text>
  <rect x="220" y="110" width="80" height="36" rx="4" fill="#e9d5ff"/><text x="260" y="132" text-anchor="middle" font-size="9">NATS</text>
  <rect x="320" y="110" width="80" height="36" rx="4" fill="#e9d5ff"/><text x="360" y="132" text-anchor="middle" font-size="9">Redis</text>
  <line x1="140" y1="60" x2="180" y2="60" stroke="#64748b"/>
  <line x1="300" y1="60" x2="340" y2="60" stroke="#64748b"/>
  <line x1="240" y1="80" x2="150" y2="110" stroke="#64748b"/>
  <line x1="240" y1="80" x2="260" y2="110" stroke="#64748b"/>
  <line x1="400" y1="80" x2="360" y2="110" stroke="#64748b"/>
</svg>"""


def render_user_timeline_svg() -> str:
    steps = ["Утро: проверить чаты", "День: файл коллеге", "Созвон из чата", "Поиск старого документа"]
    parts = []
    x = 20
    for i, s in enumerate(steps):
        parts.append(f'<circle cx="{x}" cy="40" r="8" fill="#22c55e"/>')
        parts.append(f'<text x="{x}" y="70" text-anchor="middle" font-size="9">{escape(s[:18])}</text>')
        if i < len(steps) - 1:
            parts.append(f'<line x1="{x+8}" y1="40" x2="{x+72}" y2="40" stroke="#cbd5e1"/>')
        x += 120
    return f'<svg viewBox="0 0 500 90" width="500" height="90" xmlns="http://www.w3.org/2000/svg">{"".join(parts)}</svg>'
