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
    PRODUCT_SCENARIO_FIT,
    PROS_CONS_BY_PRODUCT,
    RADAR_ONPREM,
    SCENARIO_COLUMNS,
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

    def test_scenario_fit_complete(self) -> None:
        self.assertEqual(len(SCENARIO_COLUMNS), 4)
        for pid, _label, _tier, _dep in PRODUCT_COLUMNS:
            fit = PRODUCT_SCENARIO_FIT.get(pid)
            self.assertIsNotNone(fit, msg=pid)
            for sid, _title in SCENARIO_COLUMNS:
                self.assertIn(sid, fit, msg=f"{pid}/{sid}")
                self.assertIn(fit[sid], ("✓", "~", "—"), msg=f"{pid}/{sid}={fit[sid]!r}")

    def test_scenario_matrix_render(self) -> None:
        from competitor_comparison_data import render_product_scenario_matrix_html  # noqa: PLC0415

        html = render_product_scenario_matrix_html()
        self.assertIn("scenario-matrix", html)
        self.assertIn("S1", html)
        self.assertIn("row-korus", html)

    def test_segment_specs(self) -> None:
        from competitor_comparison_data import SEGMENT_SPECS, render_segment_page_body  # noqa: PLC0415

        self.assertEqual(set(SEGMENT_SPECS.keys()), {"bank", "industry", "cloud"})
        for slug in SEGMENT_SPECS:
            body = render_segment_page_body(slug)
            self.assertIn("Korus", body)
            self.assertIn(SEGMENT_SPECS[slug]["badge"], body)

    def test_scenario_fit_not_all_dash(self) -> None:
        for pid, _label, _tier, _dep in PRODUCT_COLUMNS:
            fit = PRODUCT_SCENARIO_FIT[pid]
            self.assertTrue(
                any(v != "—" for v in fit.values()),
                msg=f"{pid}: all scenario_fit are dash",
            )

    def test_phase_a_render_blocks(self) -> None:
        from competitor_comparison_data import (  # noqa: PLC0415
            render_battle_cards_extended_html,
            render_compass_min_tco_10k_html,
            render_enterprise_saas_callout_html,
            render_fig_tco_s50k_svg,
            render_fstec_compliance_block_html,
        )

        s50 = render_fig_tco_s50k_svg()
        self.assertIn("50", s50)
        self.assertIn("svg", s50)
        callout = render_enterprise_saas_callout_html()
        self.assertIn("enterprise-saas-callout", callout)
        self.assertIn("SaaS", callout)
        extended = render_battle_cards_extended_html()
        for needle in ("Compass", "МТС Линк", "Loop", "TrueConf"):
            self.assertIn(needle, extended)
        bank = render_fstec_compliance_block_html()
        self.assertIn("ФСТЭК", bank)
        industry = render_compass_min_tco_10k_html()
        self.assertIn("compass-10k-mini", industry)

    def test_phase_b_render_blocks(self) -> None:
        from competitor_comparison_data import (  # noqa: PLC0415
            render_deployment_models_html,
            render_fig_tco_tier_c_svg,
            render_fig_tier_c_radar_svg,
            render_persona_extracts_html,
        )
        from competitor_products import tier_c_tco_chart_items  # noqa: PLC0415

        tier_c = render_fig_tco_tier_c_svg()
        self.assertIn("Compass", tier_c)
        self.assertIn("TrueConf", tier_c)
        items = tier_c_tco_chart_items()
        self.assertEqual(len(items), 3)
        deploy = render_deployment_models_html()
        self.assertIn("deployment-models", deploy)
        self.assertIn("specs/011-korus-cloud-platform", deploy)
        radar = render_fig_tier_c_radar_svg()
        self.assertIn("TrueConf", radar)
        for slug in ("bank", "industry", "cloud"):
            persona = render_persona_extracts_html(slug)
            self.assertIn(f"personas-{slug}", persona)
            self.assertIn("CFO", persona)

    def test_talk_track_render(self) -> None:
        from competitor_comparison_data import render_reading_guide_html, render_talk_track_html  # noqa: PLC0415

        track = render_talk_track_html()
        self.assertIn("talk-track", track)
        self.assertIn("5 минут", track)
        self.assertIn("45 минут", track)
        guide = render_reading_guide_html()
        self.assertIn("competitor_comparison_talktrack.html", guide)


if __name__ == "__main__":
    unittest.main()
