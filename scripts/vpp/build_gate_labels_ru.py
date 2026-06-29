#!/usr/bin/env python3
"""Ensure vpp-gate-labels-ru.json covers all comprehensive gates (spec 030)."""
from __future__ import annotations

import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST = REPO_ROOT / "specs/030-vpp-product-verification/contracts/vpp-comprehensive-gates.json"
LABELS = REPO_ROOT / "specs/030-vpp-product-verification/contracts/vpp-gate-labels-ru.json"


def main() -> None:
    ordered = json.loads(MANIFEST.read_text(encoding="utf-8"))["comprehensive_gates_ordered"]
    labels = json.loads(LABELS.read_text(encoding="utf-8"))
    out = {}
    missing = []
    for gate in ordered:
        if gate in labels:
            out[gate] = labels[gate]
        else:
            missing.append(gate)
            out[gate] = gate.replace("_", " ")
    LABELS.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(out)} labels -> {LABELS}")
    if missing:
        print(f"Missing custom labels (fallback): {', '.join(missing)}")


if __name__ == "__main__":
    main()
