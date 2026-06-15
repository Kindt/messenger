# Contract: Playwright Parity Gates (US5)

## Spec ↔ parity-matrix mapping

| Spec file | Parity domains | Min scenarios |
|-----------|----------------|---------------|
| auth-session.spec.ts | auth | login; logout or documented waiver |
| messaging-critical.spec.ts | chats, messages | send DM |
| messaging-group.spec.ts | groups | 3-user group |
| messaging-actions.spec.ts | messages | reply; edit/delete/reaction (extend) |
| files-export.spec.ts | files, export | upload; export job |
| contacts-search.spec.ts | contacts, search | search sidebar |
| profile-settings.spec.ts | users, blocks, devices | me; blocks; device register |
| conference-rtc.spec.ts | conference, media | capabilities; conf create; RTC UI or waiver |
| e2ee-capabilities.spec.ts | crypto | capabilities API; browser MLS after US7 |

## Gate rules

- Permanent `test.skip` MUST have comment linking to parity-matrix waiver row.
- Full-stack gate: all specs run; zero unexpected failures.
- CI job: optional, `continue-on-error` in deploy-messaging-smoke.yml.

## Operator evidence

- Update docs/parity/runtime-gate-report.md operator section when HANDOFF checklist run.
