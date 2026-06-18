"""Tests for capability comparison deltas."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation import capabilities as cap
from scripts.presentation.capabilities import render_comparison_deltas
from scripts.presentation.data_loader import load_competitors


def test_deltas_show_both_sides():
    html = render_comparison_deltas(load_competitors()["products"])
    assert "Где Korus сильнее" in html
    assert "Где сильнее конкуренты" in html
    assert "11 продуктов" in html


def test_focus_matrix_all_competitors():
    html = cap.render_focus_matrix(load_competitors()["products"])
    assert "Rocket.Chat" in html
    assert "TrueConf" in html
    assert html.count("tier-tag") == 11
