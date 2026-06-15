#!/usr/bin/env python3
"""Pre-commit helper: validate all Cell manifests (spec 011 T01125)."""

from __future__ import annotations

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts"))

from cell_manifest import validate_file  # noqa: E402


def main() -> int:
    cells_dir = REPO / "deploy/cloud/cells"
    errors: list[str] = []
    for path in sorted(cells_dir.glob("*.yaml")):
        file_errors = validate_file(path)
        if file_errors:
            errors.append(f"{path.relative_to(REPO)}:\n  " + "\n  ".join(file_errors))
    if errors:
        print("Cell manifest validation failed:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print(f"OK: {len(list(cells_dir.glob('*.yaml')))} manifest(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
