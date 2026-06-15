# Implementation Plan: Docker + Ansible Deployment & Autotest Suite

**Branch**: `003-docker-ansible-autotest` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)

## Summary

Introduce `deploy/ansible/` for Linux deployment orchestration over existing Compose stacks; add messaging E2E smokes, deploy acceptance orchestrator, nightly CI workflow, and Playwright critical-path tests. Windows PS1 scripts remain for local dev.

## Technical Context

**Language/Version**: Ansible 2.14+, Java 25 (unchanged), Bash/PowerShell smokes
**Primary Dependencies**: Docker Compose, existing compose files under `docker/` and `korus-web/`
**Testing**: Gradle buildIntegrity (PR); live-stack smokes (nightly); Playwright (optional CI job)
**Target Platform**: Linux x86_64 (CI, stage, prod); Windows dev unchanged
**Project Type**: Modular monolith + Docker stacks

## Constitution Check

1. **Spec-First**: Met — spec.md drives this plan.
2. **Testability**: Met — new smokes + Playwright; Gradle unchanged.
3. **Infrastructure Parity**: Met — acceptance runs on live PostgreSQL/NATS/Keycloak stack.
4. **Observability**: N/A for deploy; existing worker metrics unchanged.

## Project Structure

```text
specs/003-docker-ansible-autotest/
├── spec.md, plan.md, research.md, data-model.md, tasks.md, quickstart.md
├── contracts/deploy-contract.md, messaging-smoke-contract.md
└── checklists/requirements.md

deploy/ansible/
├── ansible.cfg, inventory/, group_vars/, roles/, playbooks/, templates/

scripts/
├── smoke-messaging-e2e.sh, smoke-deploy-acceptance.sh
├── smoke-ready.sh, smoke-auth.sh, smoke-web-parity-api.sh
├── keycloak-ensure-smoke-users.sh
└── lib/SmokeMessaging.sh

tests/e2e-web/
└── playwright specs (messaging-critical)

.github/workflows/deploy-messaging-smoke.yml
```

## Phases

| Phase | Deliverable |
|-------|-------------|
| A | Ansible MVP: common, korus_server, korus_web, korus_smoke roles |
| B | Messaging smokes + acceptance orchestrator |
| C | CI workflow + docs |
| D | Playwright P1 |

## Complexity Tracking

| Decision | Why | Alternative rejected |
|----------|-----|-------------------|
| Ansible wraps compose vs rewrite | Reuses 14-container stack | Bare-metal too costly |
| Register API for smoke users | Simpler than Keycloak admin create | Fixed csadmin only — insufficient for 3-user group |
