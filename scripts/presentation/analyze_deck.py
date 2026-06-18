#!/usr/bin/env python3
"""Analyze docs/index.html size and complexity."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HTML = ROOT / "docs" / "index.html"

THRESHOLDS = {
    "file_size_mb": (1.5, 3.0),
    "inline_svg_count": (25, 40),
    "deck_data_bytes": (200_000, 500_000),
    "estimated_dom_nodes": (8000, 15_000),
}


def analyze(html: str) -> dict[str, float | int]:
    deck_data_bytes = 0
    m = re.search(r'id="deck-data">(.*?)</script>', html, re.S)
    if m:
        deck_data_bytes = len(m.group(1).encode("utf-8"))
    return {
        "file_size_mb": len(html.encode("utf-8")) / (1024 * 1024),
        "inline_svg_count": len(re.findall(r"<svg[\s>]", html, re.I)),
        "deck_data_bytes": deck_data_bytes,
        "estimated_dom_nodes": html.count("<") // 2,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    if not HTML.is_file():
        print(f"FAIL: missing {HTML}")
        return 1

    html = HTML.read_text(encoding="utf-8")
    metrics = analyze(html)
    exit_code = 0

    for key, (warn, fail) in THRESHOLDS.items():
        val = metrics[key]
        status = "OK"
        if val > fail:
            status = "FAIL"
            exit_code = 1
        elif val > warn:
            status = "WARN"
        line = f"{key}: {val} ({status})"
        if args.verbose or status != "OK":
            print(line)

    if exit_code == 0 and not args.verbose:
        print("analyze_deck: OK")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
