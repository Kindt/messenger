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
EXTERNAL_STACK_TEMPLATE = (
    REPO_ROOT / "deploy/ansible/roles/korus_server/templates/external-stack-manifest.yaml.j2"
)
KORUS_SERVER_ENV_TEMPLATE = (
    REPO_ROOT / "deploy/ansible/roles/korus_server/templates/korus-server.env.j2"
)
FULL_SERVER_COMPOSE = REPO_ROOT / "docker/docker-compose.full-server.yml"


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

    def test_external_stack_template_uses_pack_profile_ids(self) -> None:
        text = EXTERNAL_STACK_TEMPLATE.read_text(encoding="utf-8")
        for profile_id in (
            "postgres-16-bundled",
            "s3-minio-bundled",
            "nats-2.10-bundled",
            "keycloak-24-bundled",
            "redis-7-bundled",
            "web-edge",
        ):
            self.assertIn(f"compatibility_profile: {profile_id}", text)

    def test_external_stack_manifest_is_bind_mounted_under_config(self) -> None:
        env_text = KORUS_SERVER_ENV_TEMPLATE.read_text(encoding="utf-8")
        compose_text = FULL_SERVER_COMPOSE.read_text(encoding="utf-8")

        self.assertIn("EXTERNAL_STACK_MANIFEST_DIR=", env_text)
        self.assertIn("EXTERNAL_STACK_MANIFEST_FILE=", env_text)
        self.assertIn("EXTERNAL_STACK_MANIFEST_PATH=/config/", env_text)
        self.assertIn("EXTERNAL_STACK_MANIFEST_PATH: /config/", compose_text)
        self.assertIn("${EXTERNAL_STACK_MANIFEST_DIR:-.}:/config:ro", compose_text)


if __name__ == "__main__":
    unittest.main()
