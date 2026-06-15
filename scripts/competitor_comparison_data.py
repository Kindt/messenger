"""Data and SVG charts for competitor comparison presentation."""

from __future__ import annotations

from dataclasses import dataclass
from html import escape

from tz_product_pricing import (
    PRICE_AS_OF,
    fmt_rub,
    fmt_rub_short,
    pilot_fullstack_monolith_monthly,
    pilot_profile_monthly,
    standard_profile_monthly,
)

# --- Korus anchors ---

EXPRESS_LICENSE_RUB_PER_USER_YEAR = 3_000
PACHKA_CORP_RUB_PER_USER_MONTH_YEAR = 399
VK_SAAS_RUB_PER_USER_MONTH_YEAR = 207


@dataclass(frozen=True)
class KorusAnchor:
    code: str
    profile: str
    ru: int
    peak_online: int
    peak_msg_s: int
    ram_gb: str
    infra_monthly: int
    infra_note: str = ""

    @property
    def infra_yearly(self) -> int:
        return self.infra_monthly * 12

    @property
    def infra_per_user_month(self) -> float:
        return self.infra_monthly / self.ru


# Enterprise infra — sizing estimates (mid-range), marked in notes
_ENTERPRISE_500K_MONTHLY = 1_000_000
_ENTERPRISE_1M_MONTHLY = 2_000_000

KORUS_ANCHORS: tuple[KorusAnchor, ...] = (
    KorusAnchor(
        "S-10k",
        "Standard",
        10_000,
        750,
        15,
        "~64 GB",
        pilot_fullstack_monolith_monthly(),
        "Полный функционал Standard, не пробник",
    ),
    KorusAnchor(
        "S-100k",
        "Standard",
        100_000,
        4_800,
        120,
        "~140 GB",
        standard_profile_monthly(),
    ),
    KorusAnchor(
        "E-500k",
        "Enterprise",
        500_000,
        15_000,
        400,
        "~450 GB",
        _ENTERPRISE_500K_MONTHLY,
        "Оценка; load test на stage",
    ),
    KorusAnchor(
        "E-1M",
        "Enterprise",
        1_000_000,
        20_000,
        600,
        "~0,9–1,2 TB",
        _ENTERPRISE_1M_MONTHLY,
        "Оценка; load test на stage",
    ),
)

PILOT_TRIAL = KorusAnchor(
    "Pilot",
    "Пробник",
    10_000,
    750,
    15,
    "12–16 GB",
    pilot_profile_monthly(),
    "Только POC; вне матрицы сравнения",
)


@dataclass(frozen=True)
class CompetitorRow:
    name: str
    license_yearly: int | None  # None = по КП / не применимо
    infra_yearly: int | None
    license_note: str = ""
    infra_note: str = ""
    in_matrix: bool = True

    def total_yearly(self, korus_license: int = 0) -> int | None:
        lic = self.license_yearly if self.license_yearly is not None else korus_license
        infra = self.infra_yearly if self.infra_yearly is not None else 0
        if self.license_yearly is None and self.infra_yearly is None:
            return None
        return lic + infra


def express_license_yearly(ru: int) -> int:
    return ru * EXPRESS_LICENSE_RUB_PER_USER_YEAR


def pachka_yearly(ru: int) -> int:
    return ru * PACHKA_CORP_RUB_PER_USER_MONTH_YEAR * 12


def vk_saas_yearly(ru: int) -> int:
    return ru * VK_SAAS_RUB_PER_USER_MONTH_YEAR * 12


# eXpress infra estimates (same unit rates methodology as Korus)
EXPRESS_INFRA_YEARLY: dict[int, int] = {
    10_000: 5_400_000,  # extrapolation from vendor tables @1k
    100_000: 25_000_000,  # estimate; official CP
}


def competitors_at_anchor(anchor: KorusAnchor) -> tuple[CompetitorRow, ...]:
    ru = anchor.ru
    rows = [
        CompetitorRow(
            "Korus Messenger",
            license_yearly=0,
            infra_yearly=anchor.infra_yearly,
            license_note="По политике вендора / КП",
            infra_note=anchor.infra_note or "Расчёт по ставкам infra",
        ),
        CompetitorRow(
            "eXpress Corporate",
            license_yearly=express_license_yearly(ru),
            infra_yearly=EXPRESS_INFRA_YEARLY.get(ru),
            license_note="Публичный прайс, on-prem",
            infra_note="Оценка по таблицам вендора" if ru in EXPRESS_INFRA_YEARLY else "Индивидуальный проект",
        ),
    ]
    if ru <= 100_000:
        rows.append(
            CompetitorRow(
                "Пачка (облако)",
                license_yearly=pachka_yearly(ru),
                infra_yearly=0,
                license_note="Тариф «Корпорация», год",
                infra_note="Железо заказчика не требуется",
            )
        )
        rows.append(
            CompetitorRow(
                "VK WorkSpace SaaS",
                license_yearly=vk_saas_yearly(ru),
                infra_yearly=0,
                license_note="Тариф «Базовый», год",
                infra_note="Облако VK; не on-prem",
            )
        )
    return tuple(rows)


PROS_CONS: dict[str, tuple[list[str], list[str]]] = {
    "Korus Messenger": (
        [
            "Tiered infra: низкий OPEX на 10k–100k",
            "Compliance by design: export, legal hold, dual-TTL",
            "On-prem в изолированном контуре",
            "Путь Pilot → Standard → Enterprise без смены UX",
        ],
        [
            "Меньше готового супераппа (SmartApps, почта)",
            "Mobile apps — в roadmap",
            "Formal E2EE prod sign-off pending",
        ],
    ),
    "eXpress Corporate": (
        [
            "ФСТЭК, зрелый суперапп (ВКС до 500, SmartApps)",
            "Публичный per-user прайс",
            "Мобильные клиенты iOS/Android/Аврора",
            "Федерация между организациями",
        ],
        [
            "Лицензия доминирует в TCO",
            "Sizing >1k частично закрыт (КП)",
            "Тяжёлый медиа-стек (Kafka, transcoding)",
        ],
    ),
    "Пачка (облако)": (
        [
            "Нет своего железа и DevOps",
            "SLA 99,9%, поддержка 24/7",
            "Быстрый старт",
        ],
        [
            "Не on-prem в контуре",
            "TCO @10k+ выше infra-only Korus",
            "Нет публичного server sizing",
        ],
    ),
    "VK WorkSpace SaaS": (
        [
            "Полный workspace (почта, диск, задачи)",
            "Узнаваемый бренд",
            "Известный SaaS-прайс",
        ],
        [
            "Не изолированный on-prem",
            "Мессенджer-only sizing не публикуется",
            "TCO сопоставим с eXpress по порядку",
        ],
    ),
}


def _bar_chart_svg(
    title: str,
    series: list[tuple[str, int, str]],
    *,
    width: int = 720,
    height: int = 320,
    caption: str,
    stacked: list[tuple[str, int, str]] | None = None,
) -> str:
    """Grouped or simple bar chart. Values in rubles (yearly)."""
    if not series:
        return ""
    max_v = max(v for _, v, _ in series)
    if max_v <= 0:
        max_v = 1
    margin_l, margin_b, margin_t = 72, 48, 40
    chart_h = height - margin_b - margin_t
    bar_w = min(80, (width - margin_l - 40) // max(len(series), 1) - 16)
    gap = 12
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="24" text-anchor="middle" font-size="14" font-weight="bold">{escape(title)}</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 20}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    for i in range(5):
        y = margin_t + chart_h - chart_h * i // 4
        val = max_v * i // 4
        parts.append(f'<line x1="{margin_l}" y1="{y}" x2="{width - 20}" y2="{y}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{margin_l - 6}" y="{y + 4}" text-anchor="end" font-size="9" fill="#4b5563">'
            f"{escape(fmt_rub_short(val))}</text>"
        )
    x = margin_l + 10
    for label, value, color in series:
        h = max(4, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y - 6}" text-anchor="middle" font-size="9" font-weight="600">'
            f"{escape(fmt_rub_short(value))}</text>"
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 16}" text-anchor="middle" font-size="9">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(f"</svg><figcaption class=\"fig-cap\">{escape(caption)}</figcaption></figure>")
    return "".join(parts)


def _stacked_tco_svg(
    title: str,
    items: list[tuple[str, int, int, str]],
    caption: str,
) -> str:
    """items: (label, infra_yearly, license_yearly, base_color)"""
    width, height = 720, 340
    margin_l, margin_t, margin_b = 80, 44, 56
    chart_h = height - margin_t - margin_b
    max_v = max(i + l for _, i, l, _ in items) or 1
    bar_w, gap = 100, 24
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="24" text-anchor="middle" font-size="14" font-weight="bold">{escape(title)}</text>',
        f'<rect x="{margin_l - 60}" y="{margin_t - 8}" width="12" height="12" fill="#86efac"/>'
        f'<text x="{margin_l - 44}" y="{margin_t + 2}" font-size="10">Infra</text>',
        f'<rect x="{margin_l + 40}" y="{margin_t - 8}" width="12" height="12" fill="#6366f1"/>'
        f'<text x="{margin_l + 56}" y="{margin_t + 2}" font-size="10">Лицензия</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 24}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    x = margin_l + 8
    for label, infra, lic, _color in items:
        total = infra + lic
        h_total = max(6, round(total / max_v * chart_h))
        h_infra = max(2, round(infra / total * h_total)) if total else 0
        h_lic = h_total - h_infra
        y_base = margin_t + chart_h
        y_lic = y_base - h_lic
        y_infra = y_lic - h_infra
        parts.append(f'<rect x="{x}" y="{y_lic}" width="{bar_w}" height="{h_lic}" fill="#6366f1" rx="2"/>')
        parts.append(f'<rect x="{x}" y="{y_infra}" width="{bar_w}" height="{h_infra}" fill="#86efac" rx="2"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y_infra - 6}" text-anchor="middle" font-size="9" font-weight="600">'
            f"{escape(fmt_rub_short(total))}</text>"
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 18}" text-anchor="middle" font-size="9">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(f"</svg><figcaption class=\"fig-cap\">{escape(caption)}</figcaption></figure>")
    return "".join(parts)


def render_fig_profile_floors_svg() -> str:
    return """<figure class="fig"><svg viewBox="0 0 680 200" width="680" height="200" xmlns="http://www.w3.org/2000/svg">
  <text x="340" y="22" text-anchor="middle" font-size="14" font-weight="bold">Профили Korus: пороги и якоря расчёта</text>
  <rect x="20" y="40" width="200" height="56" rx="8" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="120" y="62" text-anchor="middle" font-size="12" font-weight="600">Pilot (пробник)</text>
  <text x="120" y="80" text-anchor="middle" font-size="10" fill="#6b7280">до 10k · вне матрицы</text>
  <rect x="240" y="40" width="200" height="56" rx="8" fill="#dcfce7" stroke="#22c55e"/>
  <text x="340" y="62" text-anchor="middle" font-size="12" font-weight="600">Standard</text>
  <text x="340" y="80" text-anchor="middle" font-size="10" fill="#6b7280">floor 10k · max 100k</text>
  <rect x="460" y="40" width="200" height="56" rx="8" fill="#dbeafe" stroke="#3b82f6"/>
  <text x="560" y="62" text-anchor="middle" font-size="12" font-weight="600">Enterprise</text>
  <text x="560" y="80" text-anchor="middle" font-size="10" fill="#6b7280">floor 100k · max 1M</text>
  <line x1="120" y1="110" x2="120" y2="130" stroke="#f59e0b" stroke-dasharray="4 3"/>
  <line x1="280" y1="130" x2="400" y2="130" stroke="#22c55e" stroke-width="2"/>
  <line x1="480" y1="130" x2="600" y2="130" stroke="#3b82f6" stroke-width="2"/>
  <circle cx="280" cy="130" r="5" fill="#22c55e"/><text x="280" y="152" text-anchor="middle" font-size="9">S-10k</text>
  <circle cx="340" cy="130" r="5" fill="#22c55e"/><text x="340" y="152" text-anchor="middle" font-size="9">S-100k</text>
  <circle cx="480" cy="130" r="5" fill="#3b82f6"/><text x="480" y="152" text-anchor="middle" font-size="9">E-500k</text>
  <circle cx="560" cy="130" r="5" fill="#3b82f6"/><text x="560" y="152" text-anchor="middle" font-size="9">E-1M</text>
  <text x="340" y="178" text-anchor="middle" font-size="10" fill="#6b7280">Ниже 10k RU — не считаем Standard в production-матрице</text>
</svg><figcaption class="fig-cap">Рис. 1. Pilot — только пробник; сравнение TCO — на якорях Standard и Enterprise.</figcaption></figure>"""


def render_fig_infra_by_anchor_svg() -> str:
    series = [(a.code, a.infra_yearly, "#86efac" if a.profile == "Standard" else "#6366f1") for a in KORUS_ANCHORS]
    return _bar_chart_svg(
        "Infra Korus: годовая стоимость по якорям",
        series,
        caption=f"Только инфраструктура (аренда VM, диски, канал, ops). Ставки {PRICE_AS_OF}; не оферта.",
    )


def render_fig_tco_s10k_svg() -> str:
    anchor = KORUS_ANCHORS[0]
    items = []
    for c in competitors_at_anchor(anchor):
        if not c.in_matrix:
            continue
        infra = c.infra_yearly or 0
        lic = c.license_yearly or 0
        short = c.name.replace(" Messenger", "").replace(" Corporate", "")
        items.append((short, infra, lic, "#86efac"))
    return _stacked_tco_svg(
        "TCO за год @ 10 000 пользователей (infra + лицензия)",
        items,
        caption="Korus: лицензия = 0 (строка КП). eXpress: 3 000 ₽/user/год. Пачка/VK — облачная подписка.",
    )


def render_fig_tco_s100k_svg() -> str:
    anchor = KORUS_ANCHORS[1]
    items = []
    for c in competitors_at_anchor(anchor):
        infra = c.infra_yearly or 0
        lic = c.license_yearly or 0
        short = c.name.replace(" Messenger", "").replace(" Corporate", "")
        if c.infra_yearly is None and c.name.startswith("eXpress"):
            infra = EXPRESS_INFRA_YEARLY.get(100_000, 25_000_000)
        items.append((short, infra, lic, "#6366f1"))
    return _stacked_tco_svg(
        "TCO за год @ 100 000 пользователей (infra + лицензия)",
        items,
        caption="eXpress infra — оценка; уточняется у вендора. Korus — Standard @100k.",
    )


def render_fig_license_per_user_svg() -> str:
    """License cost per user per month at anchors."""
    anchors_ru = [10_000, 100_000]
    series = [
        ("eXpress", express_license_yearly(10_000) // 12 // 10_000, "#6366f1"),
        ("Пачка", PACHKA_CORP_RUB_PER_USER_MONTH_YEAR, "#f59e0b"),
        ("VK SaaS", VK_SAAS_RUB_PER_USER_MONTH_YEAR, "#93c5fd"),
        ("Korus infra", round(KORUS_ANCHORS[0].infra_per_user_month), "#86efac"),
    ]
    # Scale chart manually for 10k view - express = 250 rub/mo, pachka 399, vk 207, korus ~11
    width, height = 560, 260
    values = [s[1] for s in series]
    max_v = max(values) or 1
    margin_l, margin_t, chart_h = 64, 36, 160
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        '<text x="280" y="22" text-anchor="middle" font-size="13" font-weight="bold">₽/пользователь/мес @ 10 000 (лицензия vs infra Korus)</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="500" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    x = 80
    for label, value, color in series:
        h = max(8, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="72" height="{h}" fill="{color}" rx="3"/>')
        parts.append(f'<text x="{x + 36}" y="{y - 6}" text-anchor="middle" font-size="10" font-weight="600">{value} ₽</text>')
        parts.append(f'<text x="{x + 36}" y="{margin_t + chart_h + 18}" text-anchor="middle" font-size="9">{escape(label)}</text>')
        x += 96
    parts.append("</svg><figcaption class=\"fig-cap\">Рис. 5. eXpress/Пачка/VK — лицензия; Korus — только infra OPEX (лицензия в КП отдельно).</figcaption></figure>")
    return "".join(parts)


def render_korus_anchor_table_html() -> str:
    rows = []
    for a in KORUS_ANCHORS:
        rows.append(
            f"<tr><td><b>{escape(a.code)}</b></td><td>{escape(a.profile)}</td>"
            f"<td>{a.ru:,}".replace(",", " ")
            + f"</td><td>{a.peak_online:,}".replace(",", " ")
            + f"</td><td>~{a.peak_msg_s}</td><td>{escape(a.ram_gb)}</td>"
            f'<td class="money">{fmt_rub(a.infra_yearly)}</td>'
            f"<td>~{a.infra_per_user_month:.1f} ₽</td>"
            f"<td class=\"small\">{escape(a.infra_note)}</td></tr>"
        )
    return f"""
<table>
  <tr><th>Якорь</th><th>Профиль</th><th>RU</th><th>Пик онлайн</th><th>Пик msg/s</th>
      <th>RAM</th><th>Infra ₽/год</th><th>₽/user/мес</th><th>Примечание</th></tr>
  {"".join(rows)}
</table>"""


def render_comparison_matrix_html() -> str:
    blocks = []
    for anchor in KORUS_ANCHORS[:2]:  # S-10k and S-100k full matrix
        rows = []
        for c in competitors_at_anchor(anchor):
            total = c.total_yearly()
            total_s = fmt_rub(total) if total is not None else "по КП"
            if c.license_yearly is not None and c.license_yearly > 0:
                lic_s = fmt_rub(c.license_yearly)
            elif c.license_yearly == 0 and c.name.startswith("Korus"):
                lic_s = "по КП"
            else:
                lic_s = c.license_note or "—"
            infra_s = fmt_rub(c.infra_yearly) if c.infra_yearly is not None else c.infra_note
            rows.append(
                f"<tr><td>{escape(c.name)}</td>"
                f'<td class="money">{lic_s}</td><td class="money">{infra_s}</td>'
                f'<td class="money"><b>{total_s}</b></td></tr>'
            )
        blocks.append(
            f"<h3>Якорь {escape(anchor.code)} — {anchor.ru:,} пользователей".replace(",", " ")
            + "</h3>"
            + "<table><tr><th>Решение</th><th>Лицензия ₽/год</th><th>Infra ₽/год</th><th>Итого TCO</th></tr>"
            + "".join(rows)
            + "</table>"
        )
    return "".join(blocks)


def render_pros_cons_html() -> str:
    parts = []
    for name, (pros, cons) in PROS_CONS.items():
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        parts.append(
            f'<div class="case"><h4>{escape(name)}</h4>'
            f'<p><b>Плюсы:</b></p><ul>{pl}</ul>'
            f'<p><b>Минусы / ограничения:</b></p><ul>{cl}</ul></div>'
        )
    return "".join(parts)


def render_express_hardware_table_html() -> str:
    return """
<table>
  <tr><th>RU (eXpress)</th><th>vCPU Σ</th><th>RAM Σ</th><th>SSD Σ</th><th>В матрице Korus</th></tr>
  <tr><td>100</td><td>11</td><td>17 GB</td><td>431 GB</td><td class="small">Ниже floor 10k — не production</td></tr>
  <tr><td>500</td><td>37</td><td>42 GB</td><td>~1,1 TB</td><td class="small">Ниже floor 10k</td></tr>
  <tr><td>1 000</td><td>62</td><td>62 GB</td><td>~2,2 TB</td><td class="small">Ниже floor 10k</td></tr>
  <tr><td>10 000</td><td>по КП / admin guide</td><td>—</td><td>—</td><td><b>S-10k</b></td></tr>
  <tr><td>100 000</td><td>инд. проект</td><td>—</td><td>—</td><td><b>S-100k</b></td></tr>
  <tr><td>500 000+</td><td>инд. проект</td><td>—</td><td>—</td><td><b>E-500k / E-1M</b></td></tr>
</table>
<p class="small">Источник eXpress: публичная документация вендора (docs.express.ms), обзоры с таблицами sizing.</p>
"""
