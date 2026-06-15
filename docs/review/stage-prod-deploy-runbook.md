# Stage / prod deploy runbook — deploy-only (US1 + US7)

**Status (2026-06-16):** инженерная поставка **готова**. Код, Ansible, vault-шаблоны, smoke-скрипты и preflight — в репозитории.  
**Остаётся при появлении хостов:** кастомизация inventory → vault → `site.yml` → smokes → подписи в [`ops-signoff-log.md`](../../specs/004-deferred-phase2-closure/ops-signoff-log.md).

**Не требуется:** доработка приложения, новые роли Ansible, новые smoke-скрипты для US1/US7.

---

## 0. Когда стартовать

| Условие | Готово в репо | Нужно от заказчика |
|---------|---------------|-------------------|
| US1 TLS | role `tls`, `inventory/stage/`, smokes | 2 хоста, DNS, SSH, vault secrets |
| US7 E2EE | API, чеклисты, `smoke-e2ee-staging.ps1` | HTTPS URL после deploy + admin token |
| Web Push / TURN | vault→Ansible wiring, runbooks | `korus_vapid_*`, `korus_coturn_secret` в vault |

**QEMU** остаётся контуром разработки (HTTP). Stage/prod sign-off — только на реальных FQDN.

---

## 1. Однодневный чеклист ops (stage)

### A. Подготовка (Windows host)

```powershell
cd <repo-root>
.\scripts\preflight-stage-deploy.ps1 -Inventory stage
```

Исправить все `[FAIL]` до зелёного preflight.

### B. Inventory (5–15 min)

1. [`deploy/ansible/inventory/stage/hosts.yml`](../../deploy/ansible/inventory/stage/hosts.yml) — реальные `ansible_host`.
2. [`group_vars/all.yml`](../../deploy/ansible/inventory/stage/group_vars/all.yml) — LAN IP, `korus_tls_domain`, CORS, `korus_turn_host`.
3. [`group_vars/vault.yml`](../../deploy/ansible/inventory/stage/group_vars/vault.yml) из `vault.yml.example` → `ansible-vault encrypt`.

Секреты: DB, MinIO, Keycloak, JWT, coturn, **VAPID** (`korus_vapid_*`). См. [`web-push-prod-runbook.md`](web-push-prod-runbook.md).

### C. Ansible deploy (control node, Linux/WSL)

```bash
cd deploy/ansible
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass --check --diff
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass
```

### D. US1 — TLS smoke (operator workstation)

```powershell
.\scripts\stage-tls-smoke.ps1 -Inventory stage
```

Детали: [`stage-tls-smoke-runbook.md`](stage-tls-smoke-runbook.md).

### E. US7 — E2EE rows 4–6

```powershell
.\scripts\smoke-e2ee-staging.ps1 -BaseUrl "https://<korus_tls_domain>" -AdminToken "<token>"
```

Чеклист: [`e2ee-staging-checklist.md`](e2ee-staging-checklist.md).

### F. Дополнительно (рекомендуется)

```powershell
.\scripts\run-k6-stage-baseline.ps1 -BaseUrl "https://<domain>"
.\scripts\smoke-turn.ps1 -TurnHost "<domain>" -WebBaseUrl "https://<domain>"
.\scripts\playwright-staging-gate.ps1 -BaseUrl "https://<domain>"
```

### G. Sign-off

Заполнить таблицы **Ops execution** и **Signatures** в [`ops-signoff-log.md`](../../specs/004-deferred-phase2-closure/ops-signoff-log.md).

---

## 2. Prod delta (после stage green)

| Step | Command |
|------|---------|
| Inventory | `inventory/prod/hosts.yml` + prod `all.yml` |
| Deploy | `ansible-playbook -i inventory/prod/hosts.yml playbooks/site.yml --ask-vault-pass` |
| TLS tag | `ansible-playbook ... --tags tls_smoke --ask-vault-pass` |
| `MLS_STATUS=active` | Только после US7 8/8 + human sign-off |

---

## 3. Артефакты «уже в репо» (не писать заново)

| US1 / US7 | Файл |
|-----------|------|
| Stage inventory kit | `deploy/ansible/inventory/stage/README.md` |
| TLS role + nginx | `deploy/ansible/roles/tls/` |
| Vault → env | `korus_server.env.j2`, `korus-web.env.j2` |
| Preflight | `scripts/preflight-stage-deploy.ps1` |
| TLS smoke | `scripts/stage-tls-smoke.ps1`, `smoke-tls-redirect.ps1` |
| E2EE smoke | `scripts/smoke-e2ee-staging.ps1` |
| E2EE packet | `e2ee-security-signoff-packet-2026-06-15.md` |
| Sign-off matrix | `specs/004-deferred-phase2-closure/ops-signoff-log.md` |

---

## 4. Типичные сбои

| Симптом | Действие |
|---------|----------|
| preflight FAIL placeholders | Заменить `example.com` в `hosts.yml` |
| vault missing | `cp vault.yml.example vault.yml` + encrypt |
| HTTPS cert error | certbot на web host или BYO PEM (`korus_tls_use_letsencrypt: false`) |
| E2EE migrate-batch 403 | Admin token + realm role `admin` |
| wss mixed content | Проверить `WEB_CLIENT_WS_PUBLIC_URL=wss://...` в web `.env` |
