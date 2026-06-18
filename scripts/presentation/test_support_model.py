"""Tests for support_model."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.support_model import compute_support


def test_full_support_includes_all_lines():
    r = compute_support(
        12_000,
        support_mode="business",
        orgs="few",
        l1="partial",
        training="annual",
        e2ee="roadmap",
        compliance="dlp",
        dr="backup",
        staffing="outsource",
        region="region",
    )
    assert len(r.lines) == 10
    assert r.monthly_rub > 0
    assert r.rate_rub_month != 180_000  # outsource + region


def test_24x7_increases_fte():
    a = compute_support(5000, support_mode="business")
    b = compute_support(5000, support_mode="24x7")
    assert b.fte_after_mode > a.fte_after_mode
