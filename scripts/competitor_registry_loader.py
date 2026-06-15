"""Load competitor registry from scripts/competitors/registry.json."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

Tier = Literal["A", "B", "C"]

DEFAULT_REGISTRY = Path(__file__).resolve().parent / "competitors" / "registry.json"


@dataclass(frozen=True)
class LoadedRegistry:
    product_columns: tuple[tuple[str, str, Tier, str], ...]
    comparison_criteria: tuple[tuple[str, str], ...]
    product_features: dict[str, dict[str, str]]
    pros_cons_by_product: dict[str, tuple[list[str], list[str]]]
    tier_labels: dict[str, str]
    radar_axes: tuple[tuple[str, str], ...]
    radar_onprem: tuple[tuple[str, str, str, tuple[int, ...]], ...]
    radar_max: int
    loop_pro_rub_month: int
    compass_onprem_rub_month: int
    korus_s10k_infra: int


def load_registry(path: Path = DEFAULT_REGISTRY) -> LoadedRegistry:
    data = json.loads(path.read_text(encoding="utf-8"))
    criteria = tuple((c["id"], c["title"]) for c in data["criteria"])
    crit_ids = {c[0] for c in criteria}

    columns: list[tuple[str, str, Tier, str]] = []
    features: dict[str, dict[str, str]] = {}
    for p in data["products"]:
        pid = p["id"]
        tier: Tier = p["tier"]  # type: ignore[assignment]
        columns.append((pid, p["label"], tier, p["deployment"]))
        feats = dict(p["features"])
        missing = crit_ids - feats.keys()
        if missing:
            raise ValueError(f"product {pid}: missing features {sorted(missing)}")
        features[pid] = feats

    pros_cons: dict[str, tuple[list[str], list[str]]] = {}
    for name, block in data["pros_cons"].items():
        pros_cons[name] = (list(block["pros"]), list(block["cons"]))

    radar_series = tuple(
        (s["id"], s["label"], s["color"], tuple(s["scores"])) for s in data["radar"]["series"]
    )
    radar_axes = tuple((a["id"], a["label"]) for a in data["radar"]["axes"])
    if radar_series and len(radar_series[0][3]) != len(radar_axes):
        raise ValueError("radar scores length must match axes count")

    pricing = data["pricing_constants"]
    return LoadedRegistry(
        product_columns=tuple(columns),
        comparison_criteria=criteria,
        product_features=features,
        pros_cons_by_product=pros_cons,
        tier_labels=dict(data["tier_labels"]),
        radar_axes=radar_axes,
        radar_onprem=radar_series,
        radar_max=int(data["radar"].get("max", 5)),
        loop_pro_rub_month=int(pricing["loop_pro_rub_month"]),
        compass_onprem_rub_month=int(pricing["compass_onprem_rub_month"]),
        korus_s10k_infra=int(pricing["korus_s10k_infra"]),
    )
