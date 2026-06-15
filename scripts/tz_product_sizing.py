"""§10 sizing charts — recommended profile RAM (single source for Рис. 4)."""

# (label, ram_gb, profile_name)
PROFILE_RAM: tuple[tuple[str, int, str], ...] = (
    ("10k", 14, "Pilot"),
    ("100k", 140, "Standard"),
    ("500k", 450, "Enterprise"),
)

ENTERPRISE_1M_NOTE = "1M: ~900 ГБ–1,2 ТБ RAM — см. таблицу §10.3."

CHART_MAX_GB = 500


def render_fig_ram_svg() -> str:
    max_v = CHART_MAX_GB
    base_y, top_y = 200, 48
    chart_h = base_y - top_y
    bar_w = 56
    gap = 90
    x0 = 100

    bars = []
    labels = []
    for i, (scale, gb, profile) in enumerate(PROFILE_RAM):
        x = x0 + i * gap
        h = max(8, round(gb / max_v * chart_h))
        y = base_y - h
        bars.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="#6366f1" rx="3"/>')
        if h >= 18:
            bars.append(f'<text x="{x + bar_w // 2}" y="{y - 6}" text-anchor="middle" font-size="9">{gb} ГБ</text>')
        labels.append(
            f'<text x="{x + bar_w // 2}" y="218" text-anchor="middle" font-size="11">{scale}</text>'
            f'<text x="{x + bar_w // 2}" y="232" text-anchor="middle" font-size="9" fill="#6b7280">{profile}</text>'
        )

    ticks = []
    for val in (0, 100, 250, 500):
        y = base_y - chart_h * val // max_v if max_v else base_y
        if val == 0:
            y = base_y
        ticks.append(
            f'<line x1="58" y1="{y}" x2="520" y2="{y}" stroke="#e5e7eb"/>'
            f'<text x="52" y="{y + 4}" text-anchor="end" font-size="9">{val if val < 500 else "500+"}</text>'
        )

    return f"""<figure class="fig"><svg viewBox="0 0 560 250" width="560" height="250" xmlns="http://www.w3.org/2000/svg">
  <text x="280" y="22" text-anchor="middle" font-size="13" font-weight="bold">Рекомендуемая RAM по профилю (§10.3)</text>
  <line x1="58" y1="{base_y}" x2="520" y2="{base_y}" stroke="#9ca3af"/>
  <line x1="58" y1="{base_y}" x2="58" y2="{top_y}" stroke="#9ca3af"/>
  {''.join(ticks)}
  {''.join(bars)}
  {''.join(labels)}
</svg><figcaption class="fig-cap">Рис. 4. Ориентиры для планирования (не замер production). {ENTERPRISE_1M_NOTE}</figcaption></figure>"""
