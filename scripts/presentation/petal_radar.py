"""Petal (radar) diagram — tab-specific axis sets + full registry fallback."""

from __future__ import annotations

import math
import re
from html import escape
from typing import Any, Literal

from scripts.presentation.data_loader import load_competitors
from scripts.presentation.petal_scoring import explain_criteria_score, explain_user_score
from scripts.presentation.user_features import (
    COMPARE_PRODUCT_IDS,
    LEVEL_BASIC,
    LEVEL_FULL,
    LEVEL_NONE,
    LEVEL_PARTIAL,
    USER_FEATURE_GROUPS,
)

TabAxisSet = Literal["pm", "tech", "sales", "user"]

# Tier-A + on-prem alts for readable overlay (toggle in UI).
DEFAULT_SERIES_IDS = ("korus", "express", "pachka", "vk_saas", "loop", "rocket")
PM_PETAL_IDS = ("korus", "express", "pachka", "vk_saas", "loop", "trueconf")
TECH_PETAL_IDS = (
    "korus",
    "express",
    "pachka",
    "vk_saas",
    "loop",
    "rocket",
    "mattermost",
    "vk_superapp",
    "compass",
    "trueconf",
    "mts_link",
)
SALES_PETAL_IDS = ("korus", "express", "pachka", "vk_saas", "mts_link", "compass", "trueconf")
USER_PETAL_IDS = COMPARE_PRODUCT_IDS

# PM: закупка, prom-ready, комплаенс, масштаб организации.
PM_PETAL_CRITERIA = (
    "onprem",
    "export",
    "audit",
    "fstec",
    "retention",
    "multitenant",
    "sizing",
    "pricing",
    "sla",
    "superapp",
    "federation",
    "vks",
)

# Tech: эксплуатация, безопасность, интеграции, архитектура.
TECH_PETAL_CRITERIA = (
    "onprem",
    "ops",
    "e2ee",
    "search",
    "sso",
    "retention",
    "bots",
    "multitenant",
    "federation",
    "sla",
    "sizing",
)

# Sales: аргументы на встрече с ЛПР (без «внутренней кухни» ops/e2ee).
SALES_PETAL_CRITERIA = (
    "onprem",
    "fstec",
    "pricing",
    "vks",
    "mobile",
    "export",
    "audit",
    "superapp",
    "sizing",
    "sla",
)

TAB_PETAL_CRITERIA: dict[TabAxisSet, tuple[str, ...]] = {
    "pm": PM_PETAL_CRITERIA,
    "tech": TECH_PETAL_CRITERIA,
    "sales": SALES_PETAL_CRITERIA,
}

USER_AXIS_SHORT: dict[str, str] = {
    "chat": "Чат",
    "profile": "Профиль",
    "calls": "Звонки",
    "files_search": "Файлы",
    "organize": "Порядок",
    "integrations": "Интегр.",
    "notify": "Уведом.",
    "live": "Эфиры",
}

LEVEL_TO_SCORE = {
    LEVEL_FULL: 5.0,
    LEVEL_PARTIAL: 4.0,
    LEVEL_BASIC: 2.5,
    LEVEL_NONE: 0.5,
}

TAB_PETAL_HEADING: dict[TabAxisSet, str] = {
    "pm": "Лепестковая диаграмма — закупка и prom-ready",
    "tech": "Лепестковая диаграмма — эксплуатация и стек",
    "sales": "Лепестковая диаграмма — аргументация сделки",
    "user": "Лепестковая диаграмма — сценарии сотрудника",
}

def all_product_ids() -> tuple[str, ...]:
    data = load_competitors()
    order = {"A": 0, "B": 1, "C": 2}
    ranked = sorted(
        data["products"],
        key=lambda p: (order.get(str(p.get("tier", "Z")), 9), p.get("label", "")),
    )
    return tuple(p["id"] for p in ranked)


def _safe_svg_id(petal_id: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_-]", "-", petal_id)


SERIES_COLORS = {
    "korus": "#22c55e",
    "express": "#6366f1",
    "pachka": "#f97316",
    "vk_saas": "#0ea5e9",
    "loop": "#f59e0b",
    "rocket": "#ec4899",
    "mattermost": "#64748b",
    "vk_superapp": "#8b5cf6",
    "compass": "#d946ef",
    "trueconf": "#a855f7",
    "mts_link": "#ef4444",
}

AXIS_TOOLTIP: dict[str, str] = {
    "onprem": "Развёртывание в контуре заказчика",
    "export": "Экспорт переписки и legal hold",
    "audit": "Журнал аудита для комплаенса",
    "search": "Полнотекстовый поиск",
    "e2ee": "Сквозное шифрование переписки",
    "vks": "Звонки и видеоконференции",
    "mobile": "Мобильные приложения",
    "superapp": "Суперапп и мини-приложения",
    "fstec": "ФСТЭК и реестр отечественного ПО",
    "sizing": "Публичная методика sizing",
    "pricing": "Публичный прайс лицензии",
    "sla": "SLA и отказоустойчивость",
    "sso": "Единый вход и корпоративный каталог",
    "bots": "Боты и интеграции",
    "retention": "Ретенция и архив сообщений",
    "multitenant": "Несколько организаций в одном контуре",
    "federation": "Федерация и внешние домены",
    "ops": "Прозрачность стека для IT",
}

AXIS_SHORT: dict[str, str] = {
    "onprem": "On-prem",
    "export": "Экспорт",
    "audit": "Аудит",
    "search": "Поиск",
    "e2ee": "E2EE",
    "vks": "ВКС",
    "mobile": "Мобильные",
    "superapp": "SmartApps",
    "fstec": "ФСТЭК",
    "sizing": "Sizing",
    "pricing": "Прайс",
    "sla": "SLA",
    "sso": "SSO",
    "bots": "Боты",
    "retention": "Ретенция",
    "multitenant": "Multi-org",
    "federation": "Федерация",
    "ops": "Стек ops",
}


def feature_text_to_score(value: str) -> float:
    """Map competitor feature cell text to 0..5 for radar.

    Radar means coverage with caveats, not binary maturity. A feature that exists
    but needs scale/security/customer acceptance should stay visibly covered.
    """
    v = (value or "").strip().lower()
    if not v or v in ("—", "-", "нет", "✗", "x"):
        return 0.5
    if v.startswith("✓"):
        return 5.0
    if any(x in v for x in ("отдельный проект", "roadmap")):
        return 1.5
    if any(x in v for x in ("процесс", "кп", "по запросу")):
        return 2.5
    if any(x in v for x in ("◐", "част", "opt", "завис", "приёмка", "настраивается")):
        return 4.0
    if any(x in v for x in ("до 500", "до 10", "99,9", "smartapps", "ios/android", "dual-ttl", "platform")):
        return 4.0
    if any(x in v for x in ("solr", "webrtc", "keycloak", "java", "kafka", "saas", "облако", "marketplace", "iframe", "trust", "directory", "ha ref-arch")):
        return 4.0
    return 3.0


def _criteria_subset(criteria_ids: tuple[str, ...]) -> list[dict[str, Any]]:
    by_id = {c["id"]: c for c in load_competitors()["criteria"]}
    return [by_id[cid] for cid in criteria_ids if cid in by_id]


def build_petal_series(
    product_ids: tuple[str, ...] | None = None,
    criteria_ids: tuple[str, ...] | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    data = load_competitors()
    criteria = _criteria_subset(criteria_ids) if criteria_ids else data["criteria"]
    by_id = {p["id"]: p for p in data["products"]}
    ids = product_ids or DEFAULT_SERIES_IDS
    series = []
    for pid in ids:
        p = by_id.get(pid)
        if not p:
            continue
        feats = p.get("features", {})
        scores: list[float] = []
        rationales: list[dict[str, str]] = []
        for c in criteria:
            raw = str(feats.get(c["id"], "—"))
            score = feature_text_to_score(raw)
            scores.append(score)
            ax_title = AXIS_TOOLTIP.get(c["id"], c["title"])
            why, gap = explain_criteria_score(c["id"], ax_title, raw, score, pid)
            rationales.append({"why": why, "gap_to_5": gap})
        series.append(
            {
                "id": pid,
                "label": p["label"],
                "color": SERIES_COLORS.get(pid, "#94a3b8"),
                "scores": scores,
                "rationales": rationales,
            }
        )
    axes = [
        {
            "id": c["id"],
            "label": AXIS_SHORT.get(c["id"], c["title"][:14]),
            "title": AXIS_TOOLTIP.get(c["id"], c["title"]),
        }
        for c in criteria
    ]
    return series, axes


def build_user_petal_series(
    product_ids: tuple[str, ...] | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    products = {p["id"]: p for p in load_competitors()["products"]}
    ids = product_ids or USER_PETAL_IDS
    axes = [
        {
            "id": g.id,
            "label": USER_AXIS_SHORT.get(g.id, g.title[:12]),
            "title": f"{g.title}. {g.intro}",
        }
        for g in USER_FEATURE_GROUPS
    ]
    series = []
    for pid in ids:
        p = products.get(pid)
        if not p:
            continue
        scores: list[float] = []
        rationales: list[dict[str, str]] = []
        for g in USER_FEATURE_GROUPS:
            cell = g.comparisons.get(pid)
            level = cell.level if cell else LEVEL_BASIC
            score = LEVEL_TO_SCORE.get(level, 2.0)
            scores.append(score)
            why, gap = explain_user_score(g.id, pid, score)
            rationales.append({"why": why, "gap_to_5": gap})
        series.append(
            {
                "id": pid,
                "label": p["label"],
                "color": SERIES_COLORS.get(pid, "#94a3b8"),
                "scores": scores,
                "rationales": rationales,
            }
        )
    return series, axes


def build_tab_petal_series(
    axis_set: TabAxisSet,
    product_ids: tuple[str, ...] | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    if axis_set == "user":
        return build_user_petal_series(product_ids)
    criteria_ids = TAB_PETAL_CRITERIA[axis_set]
    return build_petal_series(product_ids, criteria_ids)


def _render_petal_rationale_html(series: list[dict[str, Any]], axes: list[dict[str, str]]) -> str:
    blocks: list[str] = []
    for ax_idx, ax in enumerate(axes):
        product_bits: list[str] = []
        for s in series:
            score = s["scores"][ax_idx]
            rat = s.get("rationales", [{}])[ax_idx] if s.get("rationales") else {}
            why = rat.get("why", "—")
            gap = rat.get("gap_to_5", "—")
            pid = s["id"]
            gap_html = ""
            if pid == "korus" and gap:
                gap_html = (
                    f'<p class="petal-rationale-gap"><span class="petal-rationale-label">'
                    f"До уверенного 5+ (Korus):</span> {escape(gap)}</p>"
                )
            product_bits.append(
                f'<div class="petal-rationale-product petal-series" data-series="{escape(pid)}">'
                f'<p class="petal-rationale-head">'
                f'<span class="petal-rationale-dot" style="background:{s["color"]}"></span>'
                f"<strong>{escape(s['label'])}</strong> · {score:.1f}/5</p>"
                f'<p class="petal-rationale-why"><span class="petal-rationale-label">Почему так:</span> '
                f"{escape(why)}</p>"
                f"{gap_html}"
                f"</div>"
            )
        blocks.append(
            f'<section class="petal-rationale-axis">'
            f'<h6 class="petal-rationale-axis-title">{escape(ax["label"])}'
            f'<span class="petal-rationale-axis-sub">{escape(ax["title"])}</span></h6>'
            f'{"".join(product_bits)}'
            f"</section>"
        )
    return (
        '<details class="petal-rationale">'
        "<summary>Обоснование оценок</summary>"
        '<p class="petal-rationale-lead">По каждой оси и продукту — почему выставлен балл (0–5). '
        "Шкала показывает покрытие с оговорками: 5/5 означает покрытие продуктовой функции в текущей модели, а не подписанный SLA; "
        "4/5 обычно означает «функция есть, но нужна проверка масштаба, удобства интерфейса или ИБ». "
        "Блок «До уверенного 5+» — <strong>только для Korus</strong> (что нужно для промышленной эксплуатации без завышения).</p>"
        f'{"".join(blocks)}'
        "</details>"
    )


def _polar(cx: float, cy: float, r: float, angle_rad: float) -> tuple[float, float]:
    return cx + r * math.sin(angle_rad), cy - r * math.cos(angle_rad)


def render_petal_radar_html(
    *,
    petal_id: str = "petal-radar",
    lead_html: str | None = None,
    width: int = 720,
    height: int = 680,
    product_ids: tuple[str, ...] | None = None,
    criteria_ids: tuple[str, ...] | None = None,
    axis_set: TabAxisSet | None = None,
    default_checked: int = 4,
    show_score_table: bool = True,
) -> str:
    if axis_set is not None:
        series, axes = build_tab_petal_series(axis_set, product_ids)
    else:
        series, axes = build_petal_series(product_ids, criteria_ids)
    n = len(axes)
    if n == 0 or not series:
        return "<p class='small'>Нет данных для лепестковой диаграммы.</p>"

    svg_uid = _safe_svg_id(petal_id)
    if lead_html is None:
        lead_html = (
            f"Лепестковая диаграмма по <strong>всем {n}</strong> критериям реестра "
            "конкурентов (0–5 на ось). Сравнение зрелости, не цены."
        )

    cx, cy = width / 2, height / 2 - 10
    max_r = min(width, height) * 0.36
    levels = (1, 2, 3, 4, 5)
    max_score = 5.0

    parts: list[str] = [
        f'<div class="petal-radar-wrap" id="{escape(petal_id)}" data-axes="{n}">',
        f'<p class="petal-radar-lead">{lead_html}</p>',
        '<div class="petal-radar-controls" role="group" aria-label="Показать продукты">',
    ]
    for i, s in enumerate(series):
        checked = " checked" if i < default_checked else ""
        parts.append(
            f'<label class="petal-toggle" style="--petal-color:{s["color"]}">'
            f'<span class="petal-color-marker" aria-hidden="true"></span>'
            f'<input type="checkbox" class="petal-series-cb" data-series="{escape(s["id"])}"{checked}/>'
            f' {escape(s["label"])}</label>'
        )
    parts.append("</div>")

    svg_parts: list[str] = [
        f'<svg class="petal-radar-svg" viewBox="0 0 {width} {height}" '
        f'width="100%" height="auto" xmlns="http://www.w3.org/2000/svg" '
        f'preserveAspectRatio="xMidYMid meet" '
        f'role="img" aria-label="Лепестковая диаграмма {n} критериев">',
        f'<defs><filter id="petal-glow-{svg_uid}" x="-20%" y="-20%" width="140%" height="140%">'
        f'<feDropShadow dx="0" dy="1" stdDeviation="2" flood-opacity="0.15"/></filter></defs>',
    ]

    for lvl in levels:
        rr = max_r * lvl / max_score
        ring_pts = []
        for i in range(n):
            x, y = _polar(cx, cy, rr, 2 * math.pi * i / n)
            ring_pts.append(f"{x:.1f},{y:.1f}")
        svg_parts.append(
            f'<polygon points="{" ".join(ring_pts)}" fill="none" stroke="#e2e8f0" stroke-width="1"/>'
        )

    for i, ax in enumerate(axes):
        angle = 2 * math.pi * i / n
        x2, y2 = _polar(cx, cy, max_r, angle)
        svg_parts.append(
            f'<line x1="{cx:.1f}" y1="{cy:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
            f'stroke="#cbd5e1" stroke-width="1"/>'
        )
        lx, ly = _polar(cx, cy, max_r + 22, angle)
        anchor = "middle"
        if lx < cx - 20:
            anchor = "end"
        elif lx > cx + 20:
            anchor = "start"
        svg_parts.append(
            f'<text x="{lx:.1f}" y="{ly:.1f}" text-anchor="{anchor}" '
            f'dominant-baseline="middle" font-size="10" fill="#475569">'
            f'<title>{escape(ax["title"])}</title>{escape(ax["label"])}</text>'
        )

    glow_ref = f"url(#petal-glow-{svg_uid})"
    for s in series:
        pts = []
        for i, score in enumerate(s["scores"]):
            r = max_r * max(0.08, min(score, max_score)) / max_score
            x, y = _polar(cx, cy, r, 2 * math.pi * i / n)
            pts.append(f"{x:.1f},{y:.1f}")
        pid = s["id"]
        svg_parts.append(
            f'<polygon class="petal-series" data-series="{escape(pid)}" '
            f'points="{" ".join(pts)}" fill="{s["color"]}" fill-opacity="0.18" '
            f'stroke="{s["color"]}" stroke-width="2" filter="{glow_ref}"/>'
        )
        for i, score in enumerate(s["scores"]):
            r = max_r * max(0.08, min(score, max_score)) / max_score
            x, y = _polar(cx, cy, r, 2 * math.pi * i / n)
            svg_parts.append(
                f'<circle class="petal-node petal-series" data-series="{escape(pid)}" '
                f'cx="{x:.1f}" cy="{y:.1f}" r="3" fill="{s["color"]}"/>'
            )

    svg_parts.append("</svg>")
    parts.extend(svg_parts)

    if show_score_table:
        legend_rows = []
        for ax in axes:
            cells = "".join(
                f'<td title="{escape(ax["title"])}">{s["scores"][axes.index(ax)]:.1f}</td>'
                for s in series
            )
            legend_rows.append(
                f"<tr><th scope='row' title='{escape(ax['title'])}'>{escape(ax['label'])}</th>{cells}</tr>"
            )
        header = "".join(f"<th scope='col'>{escape(s['label'])}</th>" for s in series)
        parts.append(
            '<details class="petal-score-table"><summary>Таблица оценок по осям</summary>'
            '<div class="table-wrap"><table class="petal-matrix">'
            f"<thead><tr><th scope='col'>Критерий</th>{header}</tr></thead>"
            f"<tbody>{''.join(legend_rows)}</tbody></table></div></details>"
        )
        parts.append(_render_petal_rationale_html(series, axes))
    parts.append("</div>")
    return "".join(parts)


def render_petal_section(
    *,
    petal_id: str,
    axis_set: TabAxisSet,
    lead_html: str,
    product_ids: tuple[str, ...] | None = None,
    default_checked: int = 4,
    heading: str | None = None,
) -> str:
    """Block with h4 + petal chart tailored to tab persona (PM/Tech/Sales/User)."""
    _, axes = build_tab_petal_series(axis_set, product_ids)
    n = len(axes)
    axis_word = "блоков" if axis_set == "user" else "критериев"
    title = heading or TAB_PETAL_HEADING[axis_set]
    return (
        f'<div class="petal-section" id="{escape(petal_id)}-section">'
        f'<h4 class="petal-section-head">{escape(title)} ({n} {axis_word})</h4>'
        f"{render_petal_radar_html(petal_id=petal_id, lead_html=lead_html, product_ids=product_ids, axis_set=axis_set, default_checked=default_checked)}"
        f"</div>"
    )


def petal_radar_deck_json() -> dict[str, Any]:
    series, axes = build_petal_series()
    return {"axes": axes, "series": series, "max": 5}
