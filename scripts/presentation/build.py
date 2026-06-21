#!/usr/bin/env python3
"""Build docs/index.html — orchestrate render, validate, write output."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.honesty_check import assert_honest, check_html
from scripts.presentation.render import deck_data_json, render_deck_html

OUT = ROOT / "docs" / "index.html"
MAX_MB_WARN = 1.5
MAX_MB_FAIL = 3.0


def validate_deck_data(html: str) -> None:
    start = html.find('id="deck-data">')
    if start < 0:
        raise SystemExit("Missing #deck-data")
    start = html.index(">", start) + 1
    end = html.index("</script>", start)
    json.loads(html[start:end])


def main() -> int:
    html = render_deck_html()
    assert_honest(html)
    if "рабочий прототип" not in html.lower():
        raise SystemExit("Missing prototype wording")
    validate_deck_data(html)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding="utf-8")

    size_mb = OUT.stat().st_size / (1024 * 1024)
    print(f"Wrote {OUT} ({size_mb:.2f} MB)")
    if size_mb > MAX_MB_FAIL:
        raise SystemExit(f"HTML too large: {size_mb:.2f} MB > {MAX_MB_FAIL}")
    if size_mb > MAX_MB_WARN:
        print(f"WARN: HTML size {size_mb:.2f} MB > {MAX_MB_WARN} MB")

    violations = check_html(html)
    if violations:
        raise SystemExit(f"Post-write honesty failed: {violations}")

    data = deck_data_json()
    print(
        f"Deck OK: providers={len(data['providers'])}, offerings={len(data['offerings'])}, "
        f"as_of={data['offerings_max_as_of']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
