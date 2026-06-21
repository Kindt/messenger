"""Tests for presentation data layer."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.data_loader import load_competitors, load_offerings

SCHEMA_PATH = Path(__file__).parent / "data" / "competitor_offerings.schema.json"


def test_competitors_has_eleven_products():
    data = load_competitors()
    assert len(data["products"]) == 11


def test_offerings_have_competitor_stated_ru():
    offerings = load_offerings()
    assert all(o["value"] > 0 and o["source"] for o in offerings)


def test_no_anchor_ids_in_data():
    data_dir = Path(__file__).parent / "data"
    text = "".join(f.read_text(encoding="utf-8") for f in data_dir.glob("*.json"))
    assert "S-10k" not in text and "KORUS_ANCHORS" not in text
    assert "korus-pilot" not in text and "korus-standard" not in text


def test_every_offering_has_https_source_url():
    for o in load_offerings():
        assert o["source_url"].startswith("https://"), o["id"]
        if o.get("price_is_public", True):
            assert o["source_type"] in ("public_pricing", "public_docs", "public_press")


def test_offerings_validate_against_schema():
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    data = json.loads((Path(__file__).parent / "data" / "competitor_offerings.json").read_text(encoding="utf-8"))
    jsonschema.validate(data, schema)


def test_offerings_completeness_a3d():
    o = load_offerings()
    assert len(o) >= 18
    tier_a = {"express", "pachka", "vk_saas", "korus"}
    for pid in tier_a:
        rows = [x for x in o if x["product_id"] == pid]
        assert len(rows) >= 2, pid


def test_tco_comparable_rows_are_registered_user_prices():
    for o in load_offerings():
        if o.get("tco_comparable", True):
            assert o["metric"] == "registered_users", o["id"]
            assert o["billing_unit"] == "registered_users", o["id"]
            assert o["product_id"] != "korus", o["id"]


def test_active_user_tariffs_are_not_tco_comparable():
    active = [o for o in load_offerings() if o.get("billing_unit") == "active_users"]
    assert active
    for o in active:
        assert o["metric"] == "active_users", o["id"]
        assert o["tco_comparable"] is False, o["id"]


def test_deck_data_exports_calculation_fields():
    from scripts.presentation.render import deck_data_json

    sample = deck_data_json()["offerings"][0]
    assert "metric" in sample
    assert "billing_unit" in sample
    assert "tco_comparable" in sample


def test_no_public_price_skips_tco():
    from scripts.presentation.compare_engine import build_compare_row
    from scripts.presentation.data_loader import offering_by_id

    row = build_compare_row(offering_by_id("loop-enterprise-kp"))
    assert row.competitor_total_yearly_rub is None
