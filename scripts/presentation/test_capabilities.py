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
    assert "A — пром." not in html
    assert "A — приоритетный класс сравнения" in html


def test_capability_cards_include_external_stack_positioning():
    html = cap.render_capability_cards()
    assert "Кастомизируемый внешний стек" in html
    assert "манифест желаемого состояния" in html
    assert "проверки подключения" in html
    assert "эксплуатационная приёмка" in html
    assert "BYO" not in html
    assert "desired manifest" not in html
    assert "attached probes" not in html


def test_capability_cards_use_public_terms():
    html = cap.render_capability_cards()
    for token in ("backend", "legal hold", "dual-TTL", "deep-archive", "PWA"):
        assert token.lower() not in html.lower()
    assert "юридическое удержание" in html
    assert "правила хранения" in html
    assert "ярлык «как приложение»" in html
    assert "интерфейс подготовлен на 6 языках" in html.lower()


def test_capability_cards_include_personalization():
    html = cap.render_capability_cards()
    assert "Персонализация интерфейса" in html
    assert "интерфейс консоли подготовлен на 6 языках" in html.lower()
    assert "промышленная приёмка" in html.lower()
    assert "Новые сценарии для команд" in html
    assert "подключаемые возможности" in html.lower()


def test_capability_team_scenarios_marked_done():
    html = cap.render_capability_cards()
    idx = html.index("Новые сценарии для команд")
    chunk = html[idx : idx + 400]
    assert "Реализовано" in chunk
