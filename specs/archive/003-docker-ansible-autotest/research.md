# Research: Docker + Ansible & Autotest Strategy

**Feature**: 003-docker-ansible-autotest | **Date**: 2026-05-27

## Ansible vs existing scripts

| Concern | Current | Ansible benefit |
|---------|---------|-----------------|
| Server up | `server-host-up.sh` | Inventory + idempotency + secrets templates |
| Web up | `web-host-up.sh` | Templated `.env` from `group_vars` |
| QEMU bootstrap | `vm-bootstrap/server.sh` | Reusable roles |
| CI | `full-stack-up.sh` directly | Same logic callable from playbook |

**Decision**: Roles invoke existing `scripts/full-stack-up.sh` and `korus-web-up.sh` with `SKIP_KORUS_ENSURE=1` to avoid duplicating compose flags.

## Playwright vs API smokes

Spec 002 deferred browser E2E. Spec 003 adds:
- **API/WS smokes** — mandatory, run in CI nightly
- **Playwright** — P1 critical path only (`login → group → message`)

**Decision**: Layered pyramid; PR gate stays `buildIntegrity`.

## CI runner constraints

- Full stack ~14 containers; export-compliance workflow uses 90 min timeout
- **Decision**: deploy-messaging-smoke uses same pattern; no PR gate

## Smoke user provisioning

Options evaluated:
1. Keycloak admin API create — works, more code
2. `/api/v1/auth/register` — native API, 409 on duplicate OK
3. Fixed csadmin/admin — only 2 users

**Decision**: `keycloak-ensure-smoke-users.sh` tries register first; Keycloak ensure for email/password grant compatibility.

## WS deliver assertion

WS gateway forwards NATS `msg.deliver.{userId}` as raw JSON (`MessageSendEvent`).

**Decision**: Python3 websocket client inline in smoke script; REST poll fallback if WS recv times out.
