# Ansible deployment for Korus Messenger

Idempotent Linux deployment over existing Docker Compose stacks.

## Layout

- `inventory/local/` — single node (CI, dev)
- `inventory/two-host/` — server + web LAN topology
- `inventory/stage/` — prod-like stage (TLS enabled example vars)
- `inventory/prod/` — production two-host (TLS + vault; BYO cert default)
- `playbooks/ci-local.yml` — full-server on localhost
- `playbooks/site.yml` — two-host site + optional `--tags smoke`

## Quick start

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml -e run_smoke=true
```

See [deploy/ansible/DEPLOY_QUICKSTART.md](../../deploy/ansible/DEPLOY_QUICKSTART.md).

Windows dev: use QEMU only — `.\scripts\qemu-dev-mode.ps1` (see [`deploy/qemu/README.md`](../qemu/README.md)). Linux/CI: `scripts/full-stack-up.sh`, `server-host-up.ps1` for LAN publish.

## Optional playbooks

| Playbook | Purpose |
|----------|---------|
| `observability-only.yml` | Prometheus + Grafana (`deploy/observability/`) |
| `server-only.yml` / `web-only.yml` | Partial deploy |

## Secrets and TLS (prod / stage)

**Stage prep kit (deploy-ready 2026-06-16):** [`inventory/stage/README.md`](inventory/stage/README.md) — placeholders until real hosts.  
**Deploy-only runbook (US1/US7):** [`docs/review/stage-prod-deploy-runbook.md`](../../docs/review/stage-prod-deploy-runbook.md).  
**Sign-off matrix:** [`docs/review/ops-signoff-log.md`](../../docs/review/ops-signoff-log.md).

### Prod vault checklist

1. Copy `group_vars/vault.example.yml` → `group_vars/vault.yml`.
2. Set secrets (uncomment and replace `CHANGE_ME`):
   - `korus_db_password`
   - `korus_minio_secret_key`
   - `korus_keycloak_admin_password`
   - `korus_jwt_secret`
   - `korus_coturn_secret`
   - `korus_vapid_public_key`, `korus_vapid_private_key`, `korus_vapid_subject` (Web Push)
3. Encrypt: `ansible-vault encrypt group_vars/vault.yml`
4. Run with `--ask-vault-pass` or `--vault-password-file`.
5. Confirm `roles/korus_server` rendered `docker/.env.korus-server` on the server host (mode `0600`).
6. Map is documented in `group_vars/korus_server.yml` and `roles/korus_server/templates/korus-server.env.j2`.

### Conditional vault per product add-on (spec 021 T021-051)

When using **`korus_product_addons`**, include vault keys only for installed add-ons (see [`docs/product-modules.yaml`](../../docs/product-modules.yaml) `secrets[]`):

| Add-on | Vault keys (example) | Env mapping |
|--------|----------------------|-------------|
| `addon-engage` | `korus_vapid_*` | `PUSH_VAPID_*` |
| `addon-live` | `korus_livekit_*` | `LIVEKIT_*` |
| `addon-directory` | LDAP bind (org policy) | per org |

Base secrets (DB, MinIO, JWT, Keycloak) remain required. Template comments: `group_vars/vault.example.yml`.

### Conditional vault per product add-on (spec 021 T021-051)

Secrets in `group_vars/vault.example.yml` are grouped by **add-on id** from [`docs/product-modules.yaml`](../../docs/product-modules.yaml). Include keys only when the add-on is in `korus_product_addons`:

| Add-on | Vault keys (example) | Env mapping |
|--------|----------------------|-------------|
| `addon-engage` | `korus_vapid_*` | `PUSH_VAPID_*` |
| `addon-live` | `korus_livekit_*` | `LIVEKIT_*` |
| `addon-directory` | `korus_ldap_bind_password` | org auth policy |

Runtime probe: `PlatformModuleRegistry` → `secrets_missing` degradation if env absent. Dev/CI omits vault file — templating skipped.

### core-api deploy mode (spec 021 T021-112)

`group_vars/korus_server.yml`:

```yaml
korus_core_api_deploy_mode: embedded  # embedded | war
```

Rendered to `docker/.env.korus-server` as `CORE_API_DEPLOY_MODE`. **`embedded`** — текущий full-stack (embedded Tomcat в compose). **`war`** — placeholder до bootstrap T021-100: собрать `docker/Dockerfile.core-api.war`, smoke `scripts/smoke-core-api-jetty.ps1` (QEMU guest).

### TLS checklist (stage / prod)

1. Use `inventory/stage/` or `inventory/prod/`, or set in your inventory `group_vars/all.yml`:
   - `korus_tls_enabled: true`
   - `korus_tls_domain` — public DNS name
   - `korus_tls_use_letsencrypt: true` **or** BYO paths via `korus_tls_cert_path` / `korus_tls_key_path`
2. **Let's Encrypt:** on the web host, obtain certs before or alongside first deploy:
   ```bash
   certbot certonly --nginx -d messenger.example.com
   ```
   Default cert paths: `/etc/letsencrypt/live/<domain>/fullchain.pem` and `privkey.pem`.
3. Deploy: `ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass`
4. Role `tls` installs host nginx, templates `korus-tls.conf.j2` (443 → web lb, optional `/api/` upstream), HTTP → HTTPS redirect.
5. Post-deploy smoke via playbook tag or operator workstation:
   ```bash
   ansible-playbook -i inventory/prod/hosts.yml playbooks/site.yml --tags tls_smoke --ask-vault-pass
   ```
   Or manually:
   ```powershell
   .\scripts\smoke-tls-redirect.ps1 -HttpUrl http://messenger.example.com `
     -HttpsUrl https://messenger.example.com `
     -ExpectedCertSubject messenger.example.com
   ```
6. Dev/QEMU/CI stay HTTP-only: `korus_tls_enabled` defaults to `false`. Skip TLS smoke with `-SkipTls`.

## CI

GitHub Actions: **Deploy messaging smoke** (Ansible + acceptance) and **Playwright** job in the same workflow.

## QEMU (Windows dev VMs)

Two Ubuntu guests on **192.168.76.0/24** use the same Ansible roles as bare-metal two-host:

| Guest | Playbook (inside VM) |
|-------|----------------------|
| server | `playbooks/qemu-server-local.yml` |
| web | `playbooks/qemu-web-local.yml` |

Entry point: **`deploy/qemu/vm-bootstrap/run-ansible-local.sh`**. Host redeploy: **`scripts/qemu-redeploy.ps1`**. See **`deploy/qemu/README.md`**.
