#!/usr/bin/env python3
"""Unit tests for Cell manifest validator (spec 011 T01103)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(SCRIPT_DIR))

from cell_manifest import expand_manifest, load_manifest, validate_file, validate_manifest  # noqa: E402

TEMPLATE = REPO_ROOT / "deploy/cloud/cells/_template/cell.yaml.example"
INTERNAL = REPO_ROOT / "deploy/cloud/cells/internal-dev.yaml"
ACME = REPO_ROOT / "deploy/cloud/cells/acme-prod-example.yaml"


class TestCellManifest(unittest.TestCase):
    def test_template_example_valid(self) -> None:
        errors = validate_file(TEMPLATE)
        self.assertEqual(errors, [], msg="\n".join(errors))

    def test_internal_dev_valid(self) -> None:
        errors = validate_file(INTERNAL)
        self.assertEqual(errors, [], msg="\n".join(errors))

    def test_acme_example_valid(self) -> None:
        errors = validate_file(ACME)
        self.assertEqual(errors, [], msg="\n".join(errors))

    def test_rejects_missing_billing_model(self) -> None:
        data = load_manifest(TEMPLATE)
        del data["commercial"]["billing_model"]
        errors = validate_manifest(data)
        self.assertTrue(any("billing_model" in e for e in errors))

    def test_rejects_plaintext_secret(self) -> None:
        data = load_manifest(TEMPLATE)
        data["tls"] = {"cert_ref": "vault:cells/x/tls", "password": "secret123"}
        errors = validate_manifest(data)
        self.assertTrue(any("plaintext secret" in e for e in errors))

    def test_expand_bank_preset(self) -> None:
        data = load_manifest(TEMPLATE)
        data["backup"] = {"preset": "bank"}
        expanded = expand_manifest(data)
        targets = expanded["backup"]["targets"]
        self.assertEqual(len(targets), 2)
        self.assertIn("airgap_weekly", targets[1]["id"])


if __name__ == "__main__":
    unittest.main()
