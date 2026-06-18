#!/usr/bin/env python3
"""PR gate Python checks: product deck (spec 018) + Cell manifests (spec 011)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PRESENTATION_TESTS = sorted((ROOT / "scripts" / "presentation").glob("test_*.py"))
CHECKS: list[tuple[str, list[str]]] = [
    ("presentation pytest", [sys.executable, "-m", "pytest", "-q", *[str(p) for p in PRESENTATION_TESTS]]),
    ("presentation smoke_deck", [sys.executable, str(ROOT / "scripts" / "presentation" / "smoke_deck.py")]),
    ("cell manifest", [sys.executable, str(ROOT / "scripts" / "test_cell_manifest.py")]),
]


def main() -> int:
    if not PRESENTATION_TESTS:
        print("FAIL: no scripts/presentation/test_*.py found", flush=True)
        return 1
    for label, cmd in CHECKS:
        print(f"=== {label} ===", flush=True)
        subprocess.check_call(cmd, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
