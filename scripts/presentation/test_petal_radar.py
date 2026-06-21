"""Tests for petal radar diagram."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.data_loader import load_competitors
from scripts.presentation.petal_radar import (
    PM_PETAL_CRITERIA,
    SALES_PETAL_CRITERIA,
    TECH_PETAL_CRITERIA,
    build_petal_series,
    build_tab_petal_series,
    build_user_petal_series,
    feature_text_to_score,
    render_petal_radar_html,
    render_petal_section,
)


def test_max_criteria_axes_count():
    criteria = load_competitors()["criteria"]
    _, axes = build_petal_series()
    assert len(axes) == len(criteria)
    assert len(axes) >= 18


def test_feature_text_scoring():
    assert feature_text_to_score("✓") == 5.0
    assert feature_text_to_score("—") == 0.5
    assert feature_text_to_score("◐") == 4.0
    assert feature_text_to_score("MLS (приёмка)") == 4.0
    assert feature_text_to_score("WebRTC") == 4.0
    assert feature_text_to_score("отдельный проект") < 2.0


def test_tab_axis_sets_differ():
    pm_axes = build_tab_petal_series("pm")[1]
    tech_axes = build_tab_petal_series("tech")[1]
    sales_axes = build_tab_petal_series("sales")[1]
    user_axes = build_user_petal_series()[1]

    assert len(pm_axes) == len(PM_PETAL_CRITERIA)
    assert len(tech_axes) == len(TECH_PETAL_CRITERIA)
    assert len(sales_axes) == len(SALES_PETAL_CRITERIA)
    assert len(user_axes) == 8

    pm_ids = {a["id"] for a in pm_axes}
    tech_ids = {a["id"] for a in tech_axes}
    assert pm_ids != tech_ids
    assert "ops" in tech_ids and "ops" not in pm_ids
    assert "chat" in {a["id"] for a in user_axes}


def test_petal_html_renders():
    html = render_petal_radar_html(axis_set="pm")
    assert "petal-radar-wrap" in html
    assert "petal-series-cb" in html
    assert "petal-color-marker" in html
    assert 'data-axes="12"' in html
    assert "покрытие с оговорками" in html
    assert "функция есть, но нужна проверка" in html
    assert "не подписанный SLA" in html
    assert "UX" not in html


def test_user_petal_section_heading():
    html = render_petal_section(
        petal_id="petal-radar-user-test",
        axis_set="user",
        lead_html="lead",
    )
    assert "8 блоков" in html
    assert "сценарии сотрудника" in html.lower() or "Сценарии" in html
