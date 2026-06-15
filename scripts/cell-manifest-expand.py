#!/usr/bin/env python3
"""Expand Cell manifest presets to full backup targets (spec 011 T01104)."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from cell_manifest import expand_manifest, load_manifest, validate_manifest  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Expand Cell manifest backup presets")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--json", action="store_true", help="Output JSON instead of YAML")
    args = parser.parse_args()

    try:
        data = load_manifest(args.manifest)
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1

    errors = validate_manifest(data)
    if errors:
        for err in errors:
            print(f"[FAIL] {err}", file=sys.stderr)
        return 1

    expanded = expand_manifest(data)
    if args.json:
        print(json.dumps(expanded, indent=2, ensure_ascii=False))
    else:
        try:
            import yaml  # type: ignore
        except ImportError:
            print(json.dumps(expanded, indent=2, ensure_ascii=False))
        else:
            print(yaml.safe_dump(expanded, allow_unicode=True, sort_keys=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
