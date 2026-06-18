"""Compare competitor offerings to Korus infra at competitor-stated RU."""

from __future__ import annotations

from dataclasses import dataclass
from html import escape
from typing import Any

from scripts.presentation import sizing_pricing as sp


@dataclass(frozen=True)
class CompareRow:
    offering: dict[str, Any]
    competitor_total_yearly_rub: int | None
    korus_infra_yearly_rub: int
    korus_at_competitor_ru: int
    korus_headroom_ru: int | None
    headroom_note: str
    profile_id: str


HEADROOM_NOTE = "без изменения цены/мощностей"


def _yearly_price(offering: dict[str, Any]) -> int | None:
    if not offering.get("price_is_public", True):
        return None
    price = offering["price_rub"]
    if price <= 0:
        return None
    period = offering["price_period"]
    metric = offering["metric"]
    value = offering["value"]
    if metric != "registered_users":
        return None
    if period == "year":
        return price
    if period == "month":
        # Per-user monthly → total yearly
        return price * value * 12
    return None


def build_compare_row(offering: dict[str, Any]) -> CompareRow:
    ru = offering["value"]
    profile = sp.pick_profile(ru)
    korus_yearly = sp.infra_yearly(profile)
    competitor_yearly = _yearly_price(offering)
    headroom = sp.headroom_ru(profile, ru)
    return CompareRow(
        offering=offering,
        competitor_total_yearly_rub=competitor_yearly,
        korus_infra_yearly_rub=korus_yearly,
        korus_at_competitor_ru=ru,
        korus_headroom_ru=headroom,
        headroom_note=HEADROOM_NOTE if headroom else "",
        profile_id=profile.id,
    )


def render_headroom_badge(row: CompareRow) -> str:
    if not row.korus_headroom_ru or row.korus_headroom_ru <= row.korus_at_competitor_ru:
        return ""
    n = f"{row.korus_headroom_ru:,}".replace(",", " ")
    return (
        f'<span class="chip chip-headroom" title="{escape(row.headroom_note)}">'
        f"до {n} рег. на тех же мощностях*</span>"
    )


def build_all_rows(offerings: list[dict[str, Any]]) -> list[CompareRow]:
    return [build_compare_row(o) for o in offerings if o["metric"] == "registered_users"]
