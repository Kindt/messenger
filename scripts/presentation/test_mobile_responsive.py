"""Smoke tests for mobile-friendly deck markup and CSS."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.render import render_deck_html


def test_deck_has_mobile_viewport_and_responsive_css():
    html = render_deck_html()
    assert 'name="viewport"' in html
    assert "viewport-fit=cover" in html
    assert "env(safe-area-inset" in html
    assert "@media (max-width: 768px)" in html
    assert "grid-template-columns: 1fr 1fr" in html  # tab bar 2x2 on phones


def test_petal_svg_scales_on_narrow_screens():
    html = render_deck_html()
    assert 'class="petal-radar-svg"' in html
    assert 'height="auto"' in html
    assert "preserveAspectRatio" in html
