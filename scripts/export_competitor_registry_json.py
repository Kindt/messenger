#!/usr/bin/env python3
"""Round-trip export: write scripts/competitors/registry.json from loaded registry."""

from __future__ import annotations

import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from competitor_products import (  # noqa: E402
    COMPARISON_CRITERIA,
    KORUS_S10K_INFRA,
    LOOP_PRO_RUB_MONTH,
    COMPASS_ONPREM_RUB_MONTH,
    TRUECONF_SERVER_MIN_YEARLY,
    PRODUCT_COLUMNS,
    PRODUCT_FEATURES,
    PROS_CONS_BY_PRODUCT,
    RADAR_AXES,
    RADAR_MAX,
    RADAR_ONPREM,
    TIER_LABELS,
)

OUT = SCRIPT_DIR / "competitors" / "registry.json"

data = {
    "version": 1,
    "tier_labels": TIER_LABELS,
    "criteria": [{"id": cid, "title": title} for cid, title in COMPARISON_CRITERIA],
    "products": [
        {
            "id": pid,
            "label": label,
            "tier": tier,
            "deployment": dep,
            "features": PRODUCT_FEATURES[pid],
        }
        for pid, label, tier, dep in PRODUCT_COLUMNS
    ],
    "pros_cons": {
        name: {"pros": list(pros), "cons": list(cons)} for name, (pros, cons) in PROS_CONS_BY_PRODUCT.items()
    },
    "radar": {
        "max": RADAR_MAX,
        "axes": [{"id": aid, "label": lbl} for aid, lbl in RADAR_AXES],
        "series": [
            {"id": pid, "label": lbl, "color": color, "scores": list(scores)}
            for pid, lbl, color, scores in RADAR_ONPREM
        ],
    },
    "pricing_constants": {
        "loop_pro_rub_month": LOOP_PRO_RUB_MONTH,
        "compass_onprem_rub_month": COMPASS_ONPREM_RUB_MONTH,
        "korus_s10k_infra": KORUS_S10K_INFRA,
        "trueconf_server_min_yearly": TRUECONF_SERVER_MIN_YEARLY,
    },
}

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {OUT}")
