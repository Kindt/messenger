# Deploy Quickstart (Docker + Ansible)

Canonical operator guide for **Linux CI**, **Ansible guests**, and **two-host** deploy.  
**Windows dev:** use QEMU only — [`deploy/qemu/README.md`](../qemu/README.md), `.\scripts\qemu-dev-mode.ps1`.

## Prerequisites

- Ubuntu 22.04+ or GitHub Actions runner
- Docker Engine + Compose plugin
- Ansible 2.14+ (`pip install ansible`)
- Python 3 (for WS recv in smokes)

## Single-host deploy (CI / local Linux)

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml
```

From repo root after deploy:

```bash
./scripts/smoke-deploy-acceptance.sh
```

## Two-host deploy

1. Edit `deploy/ansible/inventory/two-host/hosts.yml` with LAN IPs.
2. Set `group_vars/korus_server.yml` and `korus_web.yml` CORS/origins.
3. Run:

```bash
cd deploy/ansible
ansible-playbook -i inventory/two-host/hosts.yml playbooks/site.yml
ansible-playbook -i inventory/two-host/hosts.yml playbooks/site.yml --tags smoke
```

## Messaging E2E only

```bash
./scripts/keycloak-ensure-smoke-users.sh
./scripts/smoke-messaging-e2e.sh --url http://127.0.0.1:8080
```

## QEMU (Windows host)

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode warm
# verify:
curl http://127.0.0.1:18080/api/v1/health
curl http://127.0.0.1:19088/health
```

Bootstrap inside guests: `deploy/qemu/vm-bootstrap/run-ansible-local.sh` → `qemu-server-local.yml` / `qemu-web-local.yml`.

## Playwright (web UI)

```bash
cd tests/e2e-web
npm ci
npx playwright install chromium
PLAYWRIGHT_BASE_URL=http://127.0.0.1:19088 KORUS_API_URL=http://127.0.0.1:18080 npx playwright test
```

## CI manual trigger

GitHub Actions → **Deploy messaging smoke** → Run workflow

## Historical spec

Archived: [`specs/archive/003-docker-ansible-autotest/`](../../specs/archive/003-docker-ansible-autotest/)
