"""Cell manifest load, validate, expand (spec 011 Phase 0)."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

CELL_ID_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

STATUS_VALUES = frozenset({"planned", "provisioning", "active", "maintenance", "decommissioned"})
COMMERCIAL_MODEL = frozenset({"b_dedicated", "c_internal", "a_shared"})
BILLING_MODEL = frozenset({"infra_pass_through", "bundled_anchor", "flat_platform"})
SKU_VALUES = frozenset({"pilot", "standard", "enterprise"})
DNS_MODE = frozenset({"platform_subdomain", "customer_cname"})
PROVIDER_VALUES = frozenset({"generic", "proxmox", "openstack", "cloud-vm"})
BACKUP_PRESET = frozenset({"default", "bank", "pilot", "enterprise"})

BACKUP_PRESET_TARGETS: dict[str, list[dict[str, Any]]] = {
    "pilot": [
        {
            "id": "s3_daily",
            "provider": "s3",
            "bucket": "korus-cells",
            "prefix": "{cell_id}/daily/",
            "schedule": "0 2 * * *",
            "include": ["postgres_hot", "minio"],
            "retention_days": 30,
        }
    ],
    "default": [
        {
            "id": "s3_daily",
            "provider": "s3",
            "bucket": "korus-cells",
            "prefix": "{cell_id}/daily/",
            "schedule": "0 2 * * *",
            "include": ["postgres_hot", "minio", "keycloak_realm"],
            "retention_days": 90,
        }
    ],
    "bank": [
        {
            "id": "s3_daily",
            "provider": "s3",
            "bucket": "korus-cells",
            "prefix": "{cell_id}/daily/",
            "schedule": "0 2 * * *",
            "include": ["postgres_hot", "minio", "keycloak_realm"],
            "retention_days": 90,
        },
        {
            "id": "airgap_weekly",
            "provider": "filesystem",
            "path": "/mnt/airgap/{cell_id}/",
            "schedule": "0 3 * * 0",
            "include": ["postgres_hot_full", "minio_full"],
            "retention_weeks": 52,
        },
    ],
    "enterprise": [
        {
            "id": "s3_daily",
            "provider": "s3",
            "bucket": "korus-cells",
            "prefix": "{cell_id}/daily/",
            "schedule": "0 2 * * *",
            "include": ["postgres_hot", "minio", "keycloak_realm"],
            "retention_days": 180,
        },
        {
            "id": "wal_g",
            "provider": "s3",
            "bucket": "korus-cells-wal",
            "prefix": "{cell_id}/wal/",
            "schedule": "*/5 * * * *",
            "include": ["postgres_wal"],
            "retention_days": 7,
        },
    ],
}

SECRET_KEY_HINTS = ("password", "secret", "token", "credential", "private_key")


def load_manifest(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        data = json.loads(text)
    else:
        try:
            import yaml  # type: ignore
        except ImportError as exc:
            raise RuntimeError("PyYAML required: pip install pyyaml") from exc
        data = yaml.safe_load(text)
    if not isinstance(data, dict):
        raise ValueError(f"{path}: root must be a mapping")
    return data


def _require_mapping(obj: Any, path: str) -> dict[str, Any]:
    if not isinstance(obj, dict):
        raise ValueError(f"{path}: expected object")
    return obj


def _check_no_plaintext_secrets(obj: Any, path: str = "") -> list[str]:
    errors: list[str] = []
    if isinstance(obj, dict):
        for key, val in obj.items():
            key_path = f"{path}.{key}" if path else str(key)
            if isinstance(key, str) and any(h in key.lower() for h in SECRET_KEY_HINTS):
                if isinstance(val, str) and val and not val.startswith("vault:"):
                    errors.append(f"{key_path}: plaintext secret forbidden (use vault: ref)")
            errors.extend(_check_no_plaintext_secrets(val, key_path))
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            errors.extend(_check_no_plaintext_secrets(item, f"{path}[{i}]"))
    return errors


def validate_manifest(data: dict[str, Any]) -> list[str]:
    errors: list[str] = []

    cell_id = data.get("cell_id")
    if not isinstance(cell_id, str) or not CELL_ID_RE.match(cell_id):
        errors.append("cell_id: required [a-z0-9-]+ slug")

    status = data.get("status")
    if status not in STATUS_VALUES:
        errors.append(f"status: required one of {sorted(STATUS_VALUES)}")

    commercial = data.get("commercial")
    if not isinstance(commercial, dict):
        errors.append("commercial: required object")
    else:
        if commercial.get("model") not in COMMERCIAL_MODEL:
            errors.append(f"commercial.model: required one of {sorted(COMMERCIAL_MODEL)}")
        billing = commercial.get("billing_model")
        if billing not in BILLING_MODEL:
            errors.append("commercial.billing_model: REQUIRED (no platform default)")
        sku = commercial.get("sku")
        if sku not in SKU_VALUES:
            errors.append(f"commercial.sku: required one of {sorted(SKU_VALUES)}")

    compute = data.get("compute")
    if not isinstance(compute, dict):
        errors.append("compute: required object")
    else:
        provider = compute.get("provider")
        if provider not in PROVIDER_VALUES:
            errors.append(f"compute.provider: required one of {sorted(PROVIDER_VALUES)}")
        profile = compute.get("deploy_profile")
        if profile not in SKU_VALUES:
            errors.append(f"compute.deploy_profile: required one of {sorted(SKU_VALUES)}")
        if provider == "generic":
            for ip_key in ("server_private_ip", "web_private_ip", "web_public_ip"):
                if not compute.get(ip_key):
                    errors.append(f"compute.{ip_key}: required when provider=generic")

    dns = data.get("dns")
    if not isinstance(dns, dict):
        errors.append("dns: required object")
    else:
        mode = dns.get("mode")
        if mode not in DNS_MODE:
            errors.append(f"dns.mode: required one of {sorted(DNS_MODE)}")
        if not dns.get("fqdn"):
            errors.append("dns.fqdn: required")
        if mode == "customer_cname" and not dns.get("platform_backend"):
            errors.append("dns.platform_backend: required when dns.mode=customer_cname")

    backup = data.get("backup")
    if not isinstance(backup, dict):
        errors.append("backup: required object")
    else:
        preset = backup.get("preset")
        targets = backup.get("targets")
        if preset and preset not in BACKUP_PRESET:
            errors.append(f"backup.preset: unknown preset {preset!r}")
        if not preset and not targets:
            errors.append("backup: preset or targets required")

    images = data.get("images")
    if not isinstance(images, dict) or not images.get("tag"):
        errors.append("images.tag: required")

    errors.extend(_check_no_plaintext_secrets(data))
    return errors


def expand_manifest(data: dict[str, Any]) -> dict[str, Any]:
    out = json.loads(json.dumps(data))
    cell_id = out.get("cell_id", "unknown")
    backup = out.setdefault("backup", {})
    if backup.get("preset") and not backup.get("targets"):
        preset = backup["preset"]
        raw_targets = BACKUP_PRESET_TARGETS.get(preset, [])
        expanded: list[dict[str, Any]] = []
        for t in raw_targets:
            item = json.loads(json.dumps(t))
            for key, val in list(item.items()):
                if isinstance(val, str):
                    item[key] = val.format(cell_id=cell_id)
            expanded.append(item)
        backup["targets"] = expanded
        backup["enabled"] = backup.get("enabled", True)
    return out


def validate_file(path: Path) -> list[str]:
    try:
        data = load_manifest(path)
    except (OSError, json.JSONDecodeError, RuntimeError, ValueError) as exc:
        return [str(exc)]
    return validate_manifest(data)
