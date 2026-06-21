"""Tests for petal score rationale."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.petal_radar import build_tab_petal_series, render_petal_radar_html
from scripts.presentation.petal_scoring import explain_criteria_score, explain_user_score


def test_korus_fstec_partial_rationale():
    why, gap = explain_criteria_score("fstec", "ФСТЭК / реестр РФ", "в процессе", 2.5, "korus")
    assert "2.5" in why or "процесс" in why.lower()
    assert "ФСТЭК" in gap or "реестр" in gap.lower()


def test_competitor_has_no_gap_to_5():
    why, gap = explain_criteria_score("fstec", "ФСТЭК / реестр РФ", "✓ №4997", 5.0, "express")
    assert why
    assert gap == ""


def test_user_rationale_korus_only_gap():
    _, gap_k = explain_user_score("calls", "korus", 3.0)
    _, gap_e = explain_user_score("calls", "express", 5.0)
    assert "5+" in gap_k or "До уверенного" in gap_k
    assert gap_e == ""


def test_petal_html_gap_only_on_korus():
    html = render_petal_radar_html(axis_set="pm", product_ids=("korus", "express"))
    assert "petal-rationale" in html
    assert "Почему так:" in html
    assert "До уверенного 5+ (Korus):" in html
    assert html.count("До уверенного 5+ (Korus):") >= 1


def test_series_korus_has_gaps_competitor_empty():
    series, _ = build_tab_petal_series("tech", ("korus", "express"))
    assert series[0]["rationales"][0]["gap_to_5"]
    assert series[1]["rationales"][0]["gap_to_5"] == ""
