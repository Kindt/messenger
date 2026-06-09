# Ansible deployment for Korus Messenger

Idempotent Linux deployment over existing Docker Compose stacks.

## Layout

- `inventory/local/` — single node (CI, dev)
- `inventory/two-host/` — server + web LAN topology
- `inventory/stage/` — prod-like stage (TLS enabled example vars)
- `playbooks/ci-local.yml` — full-server on localhost
- `playbooks/site.yml` — two-host site + optional `--tags smoke`

## Quick start

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml -e run_smoke=true
```

See [specs/003-docker-ansible-autotest/quickstart.md](../../specs/003-docker-ansible-autotest/quickstart.md).

Windows dev: continue using `scripts/full-stack-up.ps1` and `scripts/server-host-up.ps1`.

## Optional playbooks

| Playbook | Purpose |
|----------|---------|
| `observability-only.yml` | Prometheus + Grafana (`deploy/observability/`) |
| `server-only.yml` / `web-only.yml` | Partial deploy |

## Secrets and TLS (prod / stage)

### Prod vault checklist

1. Copy `group_vars/vault.example.yml` → `group_vars/vault.yml`.
2. Set secrets (uncomment and replace `CHANGE_ME`):
   - `korus_db_password`
   - `korus_minio_secret_key`
   - `korus_keycloak_admin_password`
   - `korus_jwt_secret`
   - `korus_coturn_secret`
3. Encrypt: `ansible-vault encrypt group_vars/vault.yml`
4. Run with `--ask-vault-pass` or `--vault-password-file`.
5. Confirm `roles/korus_server` rendered `docker/.env.korus-server` on the server host (mode `0600`).
6. Map is documented in `group_vars/korus_server.yml` and `roles/korus_server/templates/korus-server.env.j2`.

### TLS checklist (stage / prod)

1. Use `inventory/stage/` or set in your inventory `group_vars/all.yml`:
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
5. Post-deploy smoke (from control node or operator workstation):
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
