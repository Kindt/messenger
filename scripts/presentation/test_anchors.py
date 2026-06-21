"""Deck must not reference legacy TZ/Presentation section numbers."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.honesty_check import check_html
from scripts.presentation.render import render_deck_html


def test_rendered_deck_has_no_legacy_section_refs():
    html = render_deck_html()
    violations = [v for v in check_html(html) if v.pattern == "legacy_section_ref"]
    assert not violations, violations


def test_legacy_section_pattern_detected():
    html = """
    <section id="block-0"><p>рабочий прототип</p></section>
    <div id="tab-tech"><p>sizing (§10.3).</p></div>
    """
    violations = [v for v in check_html(html) if v.pattern == "legacy_section_ref"]
    assert violations
