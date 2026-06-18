#!/usr/bin/env python3
"""Smoke test docs/index.html structure."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HTML = ROOT / "docs" / "index.html"


def main() -> int:
    if not HTML.is_file():
        print(f"FAIL: missing {HTML}")
        return 1
    html = HTML.read_text(encoding="utf-8")
    checks = [
        ("block-0", 'id="block-0"' in html),
        ("4 tabs", html.count('role="tab"') >= 4),
        ("16 subsections", len(re.findall(r'class="subsection"', html)) >= 16),
        ("deck-data", 'id="deck-data"' in html),
        ("prototype", "рабочий прототип" in html.lower()),
        ("offline", 'src="http' not in html and "src='http" not in html),
    ]
    failed = [name for name, ok in checks if not ok]
    if failed:
        print("FAIL:", ", ".join(failed))
        return 1
    print("smoke_deck: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
