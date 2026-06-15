# Spec 007: Platform & Stage Readiness

**Status:** engineering closed (2026-06-15); ops gates pending real stage host  
**Parent plan:** [`docs/plans/2026-06-15-unfinished-development-plan.md`](../../docs/plans/2026-06-15-unfinished-development-plan.md) §9

## Goal

Close **hybrid sprint D** (P2 platform hardening in repo/QEMU + P1 stage prep kit) without waiting for production stage infrastructure.

## In scope (engineering — done)

- QEMU redeploy lock PID, SSH host key refresh, wsUrl force redeploy
- Stage inventory/vault/TLS/E2EE/hotplug **prep** documents
- k6 pilot skeleton, replica lab overlay, hex register port, push preview i18n
- Guest smokes: `guest-smoke-platform-w2.sh`, `test-korus-wsurl.ps1`

## Out of scope (ops — backlog)

- Real stage DNS + `ansible-playbook` deploy (US1 rows 1–5)
- Human E2EE / hotplug sign-offs (US6–US7)
- Measured §10.2 load baseline JSON on stage

## Success criteria

See [`contracts/qemu-outer-gate-contract.md`](contracts/qemu-outer-gate-contract.md) and [`contracts/stage-tls-prep-contract.md`](contracts/stage-tls-prep-contract.md).
