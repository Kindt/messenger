# Tasks: Spec 014 — Bot-Plugin Platform

**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)

---

## Phase P0

- [x] T01401 OpenAPI contract `contracts/plugin-runtime-api.openapi.yaml`
- [x] T01402 integration-router module in core-api
- [x] T01403 DB migration: plugin_presets, plugin_instances, org_plugin_policies
- [x] T01404 Admin UI: instance wizard (L0 path first) — REST `/v1/admin/plugins` + AdminUiContributor
- [x] T01405 connector-runtime worker scaffold (hot-plug)
- [x] T01406 L0 JSON menu schema + validator
- [x] T01407 sdk/php + sdk/python stubs
- [x] T01408 Demo echo-bot-php82 + echo-bot-go
- [x] T01409 smoke-plugin-echo-php.ps1
- [x] T01410 CHANGELOG + application.properties `integrations.base.url`

## Phase P1

- [x] T01411 Jira connector profile (L1/L2)
- [x] T01412 Confluence connector profile (L1)
- [x] T01413 integrations/demos/bitrix24-crm-bot (PHP)
- [x] T01414 Outbound: webhook ingress + chat delivery
- [x] T01415 Naumen L1 adapter spec in registry

## Phase P2

- [x] T01416 exchange-bridge
- [x] T01417 storage-bridge
- [x] T01418 1c-bridge family design spike
- [x] T01419 ocr-worker on-prem
- [x] T01420 Legacy demos: java8, vbnet, powershell51

## Phase P3

- [x] T01421 ai-bridge + org LLM policy
- [x] T01422 L3 triage demo

---

## Infrastructure / QEMU

- [x] T01423 `docker-compose.vitrine.yml` (light / mock)
- [x] T01424 `docker-compose.vitrine-heavy.yml` (Bitrix dev box + Jira)
- [x] T01426 QEMU cloud-init `korus-integrations` + `config.ps1` constants (D12)
- [x] T01427 `qemu-up.ps1 -WithIntegrations` + Ansible `qemu-integrations-local.yml`
- [x] T01428 `docker-compose.integrations.yml` on guest `.30`; server `INTEGRATIONS_BASE_URL`
- [x] T01429 `scripts/qemu-integrations-up.ps1` + `smoke-plugin-qemu.ps1` (host forwards 18088–18096)

---

## Live gate (QEMU `korus-integrations`)

- [x] T01429 `smoke-integrations-gate.ps1` on host forwards (mock/auto) — 2026-06-16 QEMU green (stack restart on guest)
- [x] T01430 Playwright `plugin-integrations.spec.ts` — **3/3** with `KORUS_INTEGRATIONS_GATE_URL` (2026-06-16)
- [x] T01431 Optional live-backend verification — **`scripts/smoke-integrations-live-gate.ps1`**; live creds → **LSO-030** in [`specs/015-live-server-ops-backlog/`](../015-live-server-ops-backlog/)

## L0+ (declarative menu v2)

- [x] T01432 L0+ templates (`{{event.text}}`, `{{config.*}}`), `slash_commands`, `when` on buttons; schema v2; `L0TemplateSupport` / `L0WhenSupport`
- [x] T01433 L0 config structural validation on `POST .../instances/l0` (`L0MenuConfigValidator`)
