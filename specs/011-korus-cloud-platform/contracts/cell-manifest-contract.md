# Contract: Cell manifest schema (Spec 011)

**Validator:** `scripts/validate-cell-manifest.py` (Phase 0)  
**Schema:** `deploy/cloud/schemas/cell-manifest.schema.json`

---

## Required top-level keys

| Key | Type | Notes |
|-----|------|-------|
| `cell_id` | string | `[a-z0-9-]+`, unique in registry |
| `status` | enum | `planned` \| `provisioning` \| `active` \| `maintenance` \| `decommissioned` |
| `commercial` | object | see below |
| `compute` | object | see below |
| `dns` | object | see below |
| `backup` | object | `preset` and/or `targets` |
| `images` | object | `tag` required |

---

## `commercial` (required)

| Key | Type | Notes |
|-----|------|-------|
| `model` | enum | `b_dedicated` \| `c_internal` \| `a_shared` |
| `billing_model` | enum | **REQUIRED** — `infra_pass_through` \| `bundled_anchor` \| `flat_platform` |
| `sku` | enum | `pilot` \| `standard` \| `enterprise` |
| `sla_tier` | enum | optional; default from sku |
| `anchor_ru` | integer | optional; TCO anchor e.g. 10000 |

**Validation rule:** reject if `billing_model` absent (no platform default).

---

## `dns` (required)

| Key | Type | Notes |
|-----|------|-------|
| `mode` | enum | `platform_subdomain` \| `customer_cname` |
| `fqdn` | string | public URL host |
| `platform_backend` | string | required when `mode=customer_cname` |
| `customer_hostname` | string | optional alias |

---

## `compute` (required)

| Key | Type | Notes |
|-----|------|-------|
| `provider` | enum | `generic` \| `proxmox` \| `openstack` \| `cloud-vm` |
| `deploy_profile` | enum | `pilot` \| `standard` \| `enterprise` |
| `server_private_ip` | string | required if `provider=generic` |
| `web_private_ip` | string | required for two-host |
| `web_public_ip` | string | required for E1 edge |

---

## `backup`

Either:

- `preset`: `default` \| `bank` \| `pilot` \| `enterprise` — expanded by `cell-manifest-expand.py`

Or full `targets[]` per design §4.7.1.

---

## Secrets

Manifest MUST NOT contain plaintext passwords. Use:

```yaml
tls:
  cert_ref: vault:cells/<cell_id>/tls
backup:
  encryption:
    kms_key_ref: vault:cells/<cell_id>/backup-key
```

---

## Example minimal manifest

See `deploy/cloud/cells/_template/cell.yaml.example` (Phase 0 deliverable T01101).
