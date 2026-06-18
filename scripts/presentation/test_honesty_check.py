"""Tests for honesty_check."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pytest

from scripts.presentation.honesty_check import check_html


def test_banned_phrase_outside_block0_fails():
    html = """
    <section id="block-0"><p>рабочий прототип, не production-ready</p></section>
    <div id="tab-sales"><p>Мы production-ready лидер рынка</p></div>
    """
    violations = check_html(html)
    assert any(v.pattern == "production-ready" for v in violations)


def test_block0_negation_allowed():
    html = """
    <section id="block-0">
      <p>Korus — рабочий прототип. Продукт не готов к промышленной эксплуатации.</p>
    </section>
    <div id="tab-user"><p>Простой текст для сотрудника.</p></main>
    """
    violations = [v for v in check_html(html) if v.pattern == "promyshlennaya"]
    assert not violations


def test_user_jargon_in_user_tab():
    html = """
    <section id="block-0"><p>рабочий прототип</p></section>
    <div id="tab-user"><p>Используем JWT для входа</p></main>
    """
    violations = check_html(html)
    assert any(v.pattern == "user_jargon" for v in violations)


def test_rendered_deck_passes_honesty():
    from scripts.presentation.render import render_deck_html

    html = render_deck_html()
    violations = check_html(html)
    assert not violations, violations
