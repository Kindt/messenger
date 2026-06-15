#!/usr/bin/env python3
"""PR gate Python checks: competitor registry + Cell manifests (spec 011)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHECKS = (
    ROOT / "scripts/test_competitor_products.py",
    ROOT / "scripts/test_cell_manifest.py",
)


def main() -> int:
    for script in CHECKS:
        print(f"=== {script.relative_to(ROOT)} ===", flush=True)
        subprocess.check_call([sys.executable, str(script)], cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
