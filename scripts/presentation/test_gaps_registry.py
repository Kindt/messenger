"""Tests for consolidated gap registry."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.gaps_registry import _collect_rows, render_gaps_registry_html


def test_collect_rows_has_petal_and_user():
    rows = _collect_rows()
    domains = {r["domain"] for r in rows}
    assert "Radar (критерии ТЗ)" in domains
    assert "User (сценарии)" in domains
    assert "Модуль (partial)" in domains
    assert len(rows) >= 30


def test_render_gaps_registry_html():
    html = render_gaps_registry_html()
    assert "pm-gaps-registry" in html
    assert "Подробный реестр оговорок и проверок" in html
    assert "Оговорка / что подтвердить" in html
    assert "Несколько организаций / шардирование" in html
    assert "Мультитenant" not in html
    for token in ("TCO", "iframe", "webhook", "scope web-deck", "PWA + DND", "out of scope"):
        assert token not in html
    assert "Доработка продукта" in html
