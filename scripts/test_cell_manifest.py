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
EXTERNAL_STACK_PROFILES = REPO_ROOT / "docs/external-stack-profiles.yaml"
PRODUCT_MODULES = REPO_ROOT / "modules/core-api/src/main/resources/product-modules.yaml"


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

    def test_external_stack_profile_catalog_aliases_and_evidence_are_valid(self) -> None:
        data = load_manifest(EXTERNAL_STACK_PROFILES)
        profiles = {
            profile_id
            for component in data["components"].values()
            for profile_id in component.get("profiles", {}).keys()
        }

        aliases = data.get("compatibility_aliases", {})
        self.assertGreaterEqual(len(aliases), 6)
        for alias, target in aliases.items():
            self.assertNotIn(alias, profiles)
            self.assertIn(target, profiles)

        search = data["components"]["search"]["profiles"]["opensearch-candidate"]
        self.assertIn("search_reindex_contract_green", search.get("promotion_evidence", []))
        self.assertIn("supported_bundled_claim", search.get("unsupported_modes", []))

        postgres = data["components"]["relational-db-hot"]["profiles"]["postgres-16-external"]
        self.assertIn("customer_profile_evidence", postgres.get("promotion_evidence", []))
        self.assertIn("silent_fallback", postgres.get("unsupported_modes", []))

    def test_external_stack_default_profiles_and_product_module_refs_are_valid(self) -> None:
        stack = load_manifest(EXTERNAL_STACK_PROFILES)
        modules = load_manifest(PRODUCT_MODULES)
        components = stack["components"]
        profiles = {
            profile_id
            for component in components.values()
            for profile_id in component.get("profiles", {}).keys()
        }

        for component_id, component in components.items():
            default_profile = component.get("default_profile")
            self.assertIn(default_profile, component.get("profiles", {}), component_id)

        def assert_refs(owner: str, entry: dict[str, object]) -> None:
            for component_id in entry.get("external_stack_components", []) or []:
                self.assertIn(component_id, components, f"{owner}: {component_id}")
            for profile_id in entry.get("external_stack_profiles", []) or []:
                self.assertIn(profile_id, profiles, f"{owner}: {profile_id}")

        assert_refs("base", modules["base"])
        for addon in modules.get("addons", []) or []:
            assert_refs(addon["id"], addon)


if __name__ == "__main__":
    unittest.main()
