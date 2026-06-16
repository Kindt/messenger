#!/usr/bin/env python3
"""Validate L0 plugin menu JSON against integrations/schemas/l0-menu.schema.json."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "integrations" / "schemas" / "l0-menu.schema.json"


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: validate-l0-plugin-menu.py <menu.json>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"File not found: {path}", file=sys.stderr)
        return 1
    try:
        import jsonschema  # type: ignore
    except ImportError:
        data = json.loads(path.read_text(encoding="utf-8"))
        menu = data.get("menu")
        if not isinstance(menu, dict) or "buttons" not in menu or "root" not in menu:
            print("FAIL: basic menu/root/buttons required (install jsonschema for full validation)")
            return 1
        print("OK (basic check only; pip install jsonschema for schema validation)")
        return 0
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    data = json.loads(path.read_text(encoding="utf-8"))
    jsonschema.validate(instance=data, schema=schema)
    print(f"OK: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
