# Quickstart: Docker + Ansible & Autotests

**Feature**: 003-docker-ansible-autotest

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

## QEMU (Windows, две ВМ)

```powershell
.\scripts\qemu-up.ps1
# после подъёма:
curl http://127.0.0.1:18080/api/v1/health
curl http://127.0.0.1:19088/health
```

Bootstrap внутри гостей: **`deploy/qemu/vm-bootstrap/run-ansible-local.sh`** → Ansible playbooks **`qemu-server-local.yml`** / **`qemu-web-local.yml`**. Redeploy: **`.\scripts\qemu-redeploy.ps1`**. См. **`deploy/qemu/README.md`**.

## Playwright (web UI)

```bash
# Stack must include korus-web on :9088
cd tests/e2e-web
npm ci
npx playwright install chromium
KORUS_WEB_URL=http://127.0.0.1:9088 npx playwright test
```

## Windows dev (unchanged)

```powershell
.\scripts\full-stack-up.ps1 -Build
.\scripts\smoke-messaging-e2e.ps1
```

## CI manual trigger

GitHub Actions → **Deploy messaging smoke** → Run workflow
