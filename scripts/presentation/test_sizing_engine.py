"""Tests for RU-based sizing engine."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation import sizing_engine as se


def test_three_providers_with_sources():
    quotes = se.quote_all_providers(10_000)
    assert len(quotes) == 3
    for q in quotes:
        assert q.pricing_url.startswith("https://")
        assert q.monthly_rub > 0
        assert len(q.lines) == 4


def test_median_between_min_and_max():
    ru = 25_000
    quotes = se.quote_all_providers(ru)
    vals = [q.monthly_rub for q in quotes]
    med = se.median_monthly(ru)
    assert min(vals) <= med <= max(vals)


def test_headroom_for_small_ru():
    hr = se.headroom_ru(500)
    assert hr is not None
    assert hr > 500


def test_prod_full_at_10k_ram_anchor_range():
    est = se.estimate_resources(10_000)
    assert est.ram_gb_raw >= 32
    assert est.fulltext_search == "solr"


def test_ha_increases_resources():
    plain = se.estimate_resources(5_000)
    ha = se.estimate_resources(5_000, ha=True)
    assert ha.ram_gb_raw >= plain.ram_gb_raw
    assert ha.app_nodes >= plain.app_nodes
