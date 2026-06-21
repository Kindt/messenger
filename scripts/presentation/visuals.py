"""Inline SVG visuals — data-driven charts for the deck."""

from __future__ import annotations

from html import escape

from scripts.presentation import product_status as ps
from scripts.presentation import sizing_engine as se
from scripts.presentation.compare_engine import CompareRow, build_all_rows
from scripts.presentation.data_loader import load_offerings


def render_feature_donut_svg() -> str:
    """HTML/CSS donut — readable legend with color swatches."""
    counts = {s: len(ps.features_by_status(s)) for s in ("done", "partial", "planned", "out")}
    total = sum(counts.values()) or 1
    colors = {"done": "#22c55e", "partial": "#f59e0b", "planned": "#94a3b8", "out": "#64748b"}
    labels = {
        "done": ps.STATUS_TAG["done"][1],
        "partial": ps.STATUS_TAG["partial"][1],
        "planned": ps.STATUS_TAG["planned"][1],
        "out": ps.STATUS_TAG["out"][1],
    }
    segs = []
    acc = 0.0
    for status in ("done", "partial", "planned", "out"):
        n = counts[status]
        if n == 0:
            continue
        pct = 100 * n / total
        segs.append(f"{colors[status]} {acc:.2f}% {acc + pct:.2f}%")
        acc += pct
    gradient = ", ".join(segs)
    legend_items = []
    for status in ("done", "partial", "planned", "out"):
        n = counts[status]
        if n == 0:
            continue
        legend_items.append(
            f'<li><span class="donut-swatch" style="background:{colors[status]}"></span>'
            f"<strong>{escape(labels[status])}</strong> — {n}</li>"
        )
    return f"""<div class="donut-chart" role="img" aria-label="Распределение функций по статусу">
  <p class="donut-title">Функции продукта</p>
  <div class="donut-layout">
    <div class="donut-ring" style="background:conic-gradient({gradient})"></div>
    <ul class="donut-legend">{''.join(legend_items)}</ul>
  </div>
</div>"""


def render_tco_chart_html(rows: list[CompareRow], limit: int = 6) -> str:
    """Responsive HTML bar chart — competitor vs Korus server estimate (₽/год)."""
    tco_rows = [r for r in rows if r.competitor_total_yearly_rub is not None][:limit]
    if not tco_rows:
        return '<p class="small">Нет строк с публичным TCO для диаграммы.</p>'
    max_v = max(max(r.competitor_total_yearly_rub or 0, r.korus_infra_yearly_rub) for r in tco_rows)
    parts = [
        '<div class="tco-chart" role="img" aria-label="Сравнение бюджетного ориентира">',
        '<div class="tco-legend">'
        '<span><i class="swatch swatch-comp"></i> Лицензия/облако конкурента, ₽/год</span>'
        '<span><i class="swatch swatch-korus"></i> Серверы Korus (медиана 3 провайдеров), ₽/год на тот же масштаб</span>'
        "</div>",
    ]
    for row in tco_rows:
        label = escape(row.offering["label"])
        ru = f'{row.korus_at_competitor_ru:,}'.replace(",", " ")
        comp = row.competitor_total_yearly_rub or 0
        kor = row.korus_infra_yearly_rub
        w_comp = max(2, int(100 * comp / max_v)) if max_v else 0
        w_kor = max(2, int(100 * kor / max_v)) if max_v else 0
        parts.append(
            f'<div class="tco-row">'
            f'<div class="tco-label" title="{label}"><strong>{label[:28]}</strong>'
            f'<span class="tco-ru">{ru} пользователей</span></div>'
            f'<div class="tco-bars">'
            f'<div class="bar-comp" style="width:{w_comp}%"></div>'
            f'<div class="bar-korus" style="width:{w_kor}%"></div>'
            f"</div>"
            f'<div class="tco-vals">{se.fmt_rub(comp)}<br/>{se.fmt_rub(kor)}</div>'
            f"</div>"
        )
    parts.append("</div>")
    return "".join(parts)


def render_tco_bars_svg(rows: list[CompareRow], limit: int = 6) -> str:
    """Deprecated — use render_tco_chart_html."""
    return render_tco_chart_html(rows, limit)


def render_ram_bar_svg(ru: int) -> str:
    """RAM inside the selected VM tier."""
    est = se.estimate_resources(ru)
    tier = max(est.ram_gb_billed, 1)
    bar_w = 300
    used_w = min(bar_w, int(bar_w * est.ram_gb_raw / tier))
    pct = int(100 * est.ram_gb_raw / tier)
    head = ""
    hr = se.headroom_ru(ru)
    if hr and hr > ru:
        head = f'<text x="320" y="48" font-size="10" fill="#22c55e">запас до {hr:,} пользователей</text>'.replace(",", " ")
    return f"""<svg viewBox="0 0 480 78" width="100%" max-width="480" height="78" xmlns="http://www.w3.org/2000/svg">
  <text x="0" y="14" font-size="11">@ {ru:,} пользователей: ~{est.ram_gb_raw} ГБ RAM из размера {tier} ГБ ({pct}%)</text>
  <rect x="0" y="24" width="{bar_w}" height="16" fill="#e5e7eb" stroke="#94a3b8" stroke-width="1"/>
  <rect x="0" y="24" width="{used_w}" height="16" fill="#22c55e"/>
  {head}
  <text x="0" y="62" font-size="9" fill="#64748b">Расчёт полного промышленного состава; нагрузочная проверка — отдельный этап</text>
  <text x="0" y="74" font-size="9" fill="#64748b">Не схема стека — см. «Стек по узлам» ниже</text>
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
    steps = ["Проверить чаты", "Отправить файл", "Созвон из чата", "Найти документ"]
    width = 600
    height = 86
    pad_x = 78
    n = len(steps)
    step_w = (width - 2 * pad_x) / (n - 1) if n > 1 else 0.0
    parts = []
    for i, label in enumerate(steps):
        cx = pad_x + i * step_w
        parts.append(f'<circle cx="{cx:.0f}" cy="30" r="9" fill="#6366f1"/>')
        parts.append(
            f'<text x="{cx:.0f}" y="56" text-anchor="middle" font-size="10" fill="#374151">'
            f"{escape(label)}</text>"
        )
        if i < n - 1:
            nx = pad_x + (i + 1) * step_w
            parts.append(
                f'<line x1="{cx + 11:.0f}" y1="30" x2="{nx - 11:.0f}" y2="30" '
                f'stroke="#cbd5e1" stroke-width="2"/>'
            )
    return (
        f'<svg viewBox="0 0 {width} {height}" width="100%" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Типичный рабочий день" '
        f'preserveAspectRatio="xMidYMid meet">{"".join(parts)}</svg>'
    )


def render_roadmap_svg() -> str:
    items = [
        ("Сейчас", "ядро чатов", "#22c55e"),
        ("Частично", "звонки, SSO", "#f59e0b"),
        ("2026+", "стенд заказчика", "#94a3b8"),
        ("План развития", "масштаб и эфиры", "#64748b"),
    ]
    parts = ['<text x="260" y="16" text-anchor="middle" font-size="12" font-weight="bold" fill="#1e3a5f">Дорожная карта (факты репозитория)</text>']
    x = 30
    for label, sub, color in items:
        parts.append(f'<rect x="{x}" y="28" width="110" height="52" rx="8" fill="{color}" opacity="0.15" stroke="{color}"/>')
        parts.append(f'<text x="{x+55}" y="48" text-anchor="middle" font-size="10" font-weight="600" fill="#1e3a5f">{escape(label)}</text>')
        parts.append(f'<text x="{x+55}" y="66" text-anchor="middle" font-size="9" fill="#4b5563">{escape(sub)}</text>')
        if x < 400:
            parts.append(f'<text x="{x+118}" y="55" font-size="14" fill="#9ca3af">→</text>')
        x += 125
    return f'<svg viewBox="0 0 520 90" width="100%" max-width="520" height="90" xmlns="http://www.w3.org/2000/svg">{"".join(parts)}</svg>'
