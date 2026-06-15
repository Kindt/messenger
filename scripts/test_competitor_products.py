#!/usr/bin/env python3
"""Unit tests for competitor comparison product registry."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from competitor_registry_loader import DEFAULT_REGISTRY, load_registry  # noqa: E402
from competitor_products import (  # noqa: E402
    COMPARISON_CRITERIA,
    PRODUCT_COLUMNS,
    PRODUCT_FEATURES,
    PROS_CONS_BY_PRODUCT,
    RADAR_ONPREM,
    validate_product_registry,
)


class TestCompetitorProducts(unittest.TestCase):
    def test_registry_complete(self) -> None:
        errors = validate_product_registry()
        self.assertEqual(errors, [], msg="\n".join(errors))

    def test_eleven_products(self) -> None:
        self.assertEqual(len(PRODUCT_COLUMNS), 11)

    def test_eighteen_criteria(self) -> None:
        self.assertEqual(len(COMPARISON_CRITERIA), 18)

    def test_pros_cons_names(self) -> None:
        expected = {
            "Korus Messenger",
            "eXpress Corporate",
            "Пачка (облако)",
            "VK WorkSpace SaaS",
            "Loop",
            "Rocket.Chat",
            "Mattermost EE",
            "VK Superapp on-prem",
            "МТС Линк Чаты",
            "Compass",
            "TrueConf Server",
        }
        self.assertEqual(set(PROS_CONS_BY_PRODUCT.keys()), expected)

    def test_radar_onprem_series(self) -> None:
        self.assertGreaterEqual(len(RADAR_ONPREM), 6)
        for _pid, _lbl, _color, scores in RADAR_ONPREM:
            self.assertEqual(len(scores), 6)

    def test_registry_json_loads(self) -> None:
        reg = load_registry(DEFAULT_REGISTRY)
        self.assertEqual(len(reg.product_columns), 11)
        self.assertEqual(len(reg.comparison_criteria), 18)

    def test_segment_specs(self) -> None:
        from competitor_comparison_data import SEGMENT_SPECS, render_segment_page_body  # noqa: PLC0415

        self.assertEqual(set(SEGMENT_SPECS.keys()), {"bank", "industry", "cloud"})
        for slug in SEGMENT_SPECS:
            body = render_segment_page_body(slug)
            self.assertIn("Korus", body)
            self.assertIn(SEGMENT_SPECS[slug]["badge"], body)


if __name__ == "__main__":
    unittest.main()
