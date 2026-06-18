"""Infra pricing facade — re-exports sizing_engine for deck calculators and compare."""

from __future__ import annotations

from scripts.presentation import sizing_engine as se

PRICE_AS_OF = se.PRICE_AS_OF
PRICE_REGION = "РФ (Москва / СПб, коммерческий сегмент)"
PRICE_VAT = se.PRICE_VAT
FTE_RATE_RUB_PER_MONTH = se.FTE_RATE_RUB_PER_MONTH

PRICE_SOURCES: list[tuple[str, str]] = [
    (p.label, p.source_note) for p in se.PROVIDERS
]

PROVIDERS = se.PROVIDERS
fmt_rub = se.fmt_rub
estimate_resources = se.estimate_resources
quote_all_providers = se.quote_all_providers
quote_provider = se.quote_provider
median_monthly = se.median_monthly
median_yearly = se.median_yearly
headroom_ru = se.headroom_ru

# Legacy names for tests / compare_engine field
def pick_profile(registered_users: int):
    """Deprecated: returns estimate only (no Pilot/Standard labels)."""
    return estimate_resources(registered_users)


def infra_monthly(registered_users: int | se.ResourceEstimate) -> int:
    if isinstance(registered_users, se.ResourceEstimate):
        ru = registered_users.registered_users
    else:
        ru = registered_users
    return median_monthly(ru)


def infra_yearly(registered_users: int | se.ResourceEstimate) -> int:
    return infra_monthly(registered_users) * 12


def infra_breakdown_lines(registered_users: int) -> list[tuple[str, int]]:
    """Median breakdown labels across providers (for compact tables)."""
    quotes = quote_all_providers(registered_users)
    labels = [ln.label for ln in quotes[0].lines]
    out: list[tuple[str, int]] = []
    for label in labels:
        vals = []
        for q in quotes:
            for ln in q.lines:
                if ln.label == label:
                    vals.append(ln.amount_rub_month)
        out.append((label, round(sum(vals) / len(vals))))
    total = sum(a for _, a in out)
    med = median_monthly(registered_users)
    if total != med and out:
        diff = med - total
        lbl, amt = out[-1]
        out[-1] = (lbl, amt + diff)
    return out
