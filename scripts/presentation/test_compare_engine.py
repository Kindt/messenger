"""Tests for compare_engine."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.compare_engine import build_compare_row
from scripts.presentation.data_loader import offering_by_id


def test_korus_matches_competitor_ru_not_anchor():
    offering = offering_by_id("express-onprem-10k")
    row = build_compare_row(offering)
    assert row.korus_at_competitor_ru == 10_000
    assert len(row.korus_providers) == 3
    assert all(p.yearly_rub > 0 for p in row.korus_providers)


def test_headroom_when_profile_allows_more():
    offering = offering_by_id("loop-pro-500")
    row = build_compare_row(offering)
    assert row.korus_headroom_ru is not None
    assert row.korus_headroom_ru > 500
    assert "без изменения" in row.headroom_note


def test_headroom_at_large_ru():
    offering = offering_by_id("express-onprem-100k")
    row = build_compare_row(offering)
    # В том же VM-тире может быть запас RU сверх 100k
    assert row.korus_headroom_ru is None or row.korus_headroom_ru >= 100_000
