#!/usr/bin/env python3
"""Resolve Base + add-ons to infra set (ProductComposer, spec 021)."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    yaml = None  # type: ignore


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = REPO_ROOT / "docs" / "product-modules.yaml"


def load_catalog(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    if yaml is not None:
        return yaml.safe_load(text)
    raise RuntimeError("PyYAML required: pip install pyyaml")


def addon_by_id(catalog: dict) -> dict[str, dict]:
    return {a["id"]: a for a in catalog.get("addons", [])}


def resolve_infra(catalog: dict, selected_addons: list[str]) -> dict:
    base = catalog.get("base", {})
    infra: set[str] = set(base.get("core_infra", []))
    profiles: set[str] = set()
    warnings: list[str] = []

    addons = addon_by_id(catalog)
    for aid in selected_addons:
        if aid not in addons:
            raise ValueError(f"Unknown add-on: {aid}")
        spec = addons[aid]
        infra.update(spec.get("internal_infra", []))
        profiles.update(spec.get("compose_profiles", []))

    if "addon-bots" in selected_addons and "addon-integrations" in selected_addons:
        warnings.append(
            "WARN: addon-bots and addon-integrations both selected; "
            "worker-bot-delivery counted once"
        )

    return {
        "base_id": base.get("id"),
        "addons": selected_addons,
        "infra": sorted(infra),
        "compose_profiles": sorted(profiles),
        "warnings": warnings,
    }


def legacy_profile_addons(catalog: dict, profile: str) -> list[str]:
    mapping = catalog.get("legacy_deploy_profile_map", {})
    entry = mapping.get(profile)
    if not entry:
        raise ValueError(f"Unknown legacy profile: {profile}")
    return list(entry.get("addons", []))


def main() -> int:
    parser = argparse.ArgumentParser(description="ProductComposer CLI")
    parser.add_argument(
        "--catalog",
        type=Path,
        default=DEFAULT_CATALOG,
        help="Path to product-modules.yaml",
    )
    parser.add_argument(
        "--addons",
        nargs="*",
        default=None,
        help="Add-on ids (empty = Base only)",
    )
    parser.add_argument(
        "--legacy-profile",
        choices=["pilot", "standard", "enterprise"],
        default=None,
        help="Resolve from legacy_deploy_profile_map",
    )
    parser.add_argument("--json", action="store_true", help="JSON output")
    args = parser.parse_args()

    catalog = load_catalog(args.catalog)
    if args.legacy_profile:
        selected = legacy_profile_addons(catalog, args.legacy_profile)
    else:
        selected = args.addons if args.addons is not None else []

    result = resolve_infra(catalog, selected)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        for w in result["warnings"]:
            print(w, file=sys.stderr)
        print(f"base={result['base_id']} addons={result['addons']}")
        print(f"infra ({len(result['infra'])}): {', '.join(result['infra'])}")
        print(f"profiles: {', '.join(result['compose_profiles']) or '(none)'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
