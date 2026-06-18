"""Tests for calculators."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.calculators import sales_tco, support_cost, tech_capacity, user_scenario


def test_sales_tco_monthly_at_arbitrary_ru():
    r = sales_tco(registered_users=7_500, profile="auto", deployment="on_prem")
    assert r.monthly_rub > 0
    assert r.profile_picked == "pilot"


def test_tech_capacity_at_competitor_ru():
    r = tech_capacity(registered_users=500)
    assert r.total_ram_gb >= 14
    assert r.headroom_ru >= 500


def test_support_cost_scales_with_ru():
    r = support_cost(registered_users=12_000, sla="business", include_updates=True)
    assert r.fte_monthly > 0
    assert r.monthly_rub > 0


def test_user_scenario_steps():
    r = user_scenario("message")
    assert len(r.steps) >= 3
