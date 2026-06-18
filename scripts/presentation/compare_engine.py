"""Compare competitor offerings to Korus infra at competitor-stated RU."""

from __future__ import annotations

from dataclasses import dataclass
from html import escape
from typing import Any

from scripts.presentation import sizing_engine as se


@dataclass(frozen=True)
class ProviderYearly:
    provider_id: str
    label: str
    yearly_rub: int
    pricing_url: str


@dataclass(frozen=True)
class CompareRow:
    offering: dict[str, Any]
    competitor_total_yearly_rub: int | None
    korus_infra_yearly_rub: int
    korus_providers: tuple[ProviderYearly, ...]
    korus_at_competitor_ru: int
    korus_headroom_ru: int | None
    headroom_note: str
    korus_ram_gb_raw: int
    korus_ram_gb_billed: int
    korus_vcpu: int


HEADROOM_NOTE = "без изменения VM-тира (округлённая RAM)"


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
        return price * value * 12
    return None


def build_compare_row(offering: dict[str, Any]) -> CompareRow:
    ru = offering["value"]
    est = se.estimate_resources(ru)
    quotes = se.quote_all_providers(ru)
    providers = tuple(
        ProviderYearly(q.provider_id, q.provider_label, q.yearly_rub, q.pricing_url)
        for q in quotes
    )
    korus_yearly = se.median_yearly(ru)
    competitor_yearly = _yearly_price(offering)
    headroom = se.headroom_ru(ru)
    return CompareRow(
        offering=offering,
        competitor_total_yearly_rub=competitor_yearly,
        korus_infra_yearly_rub=korus_yearly,
        korus_providers=providers,
        korus_at_competitor_ru=ru,
        korus_headroom_ru=headroom,
        headroom_note=HEADROOM_NOTE if headroom else "",
        korus_ram_gb_raw=est.ram_gb_raw,
        korus_ram_gb_billed=est.ram_gb_billed,
        korus_vcpu=est.vcpu,
    )


def render_headroom_badge(row: CompareRow) -> str:
    if not row.korus_headroom_ru or row.korus_headroom_ru <= row.korus_at_competitor_ru:
        return ""
    n = f"{row.korus_headroom_ru:,}".replace(",", " ")
    return (
        f'<span class="chip chip-headroom" title="{escape(row.headroom_note)}">'
        f"до {n} рег. на том же VM-тире*</span>"
    )


def build_all_rows(offerings: list[dict[str, Any]]) -> list[CompareRow]:
    return [build_compare_row(o) for o in offerings if o["metric"] == "registered_users"]
