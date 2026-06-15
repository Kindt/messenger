#!/usr/bin/env python3
"""Validate Cell manifest YAML/JSON (spec 011 T01103)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from cell_manifest import validate_file  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate Korus Cloud Cell manifest")
    parser.add_argument("manifest", type=Path, help="Path to cell.yaml or .json")
    args = parser.parse_args()
    errors = validate_file(args.manifest)
    if errors:
        for err in errors:
            print(f"[FAIL] {err}", file=sys.stderr)
        return 1
    print(f"[OK] {args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
