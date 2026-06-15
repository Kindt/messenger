# Hotplug governance — sign-off request template (spec 004 US6)

Send to Architecture Owner, Product Owner, and Ops/SRE when ready to approve bounded deployment split ([`ADR-hotplug-deployment-split.md`](../adr/ADR-hotplug-deployment-split.md)).

---

## Subject

`Korus Messenger — Hotplug ADR sign-off (indexer split)`

## Body

```
Здравствуйте,

Просим formal sign-off по ADR «Bounded deployment split» (hot-plug workers, первый scope — indexer).

Контекст:
- ADR: docs/adr/ADR-hotplug-deployment-split.md
- Constitution exception: docs/proposals/constitution-v1.1-hotplug-bounded-exception.md
- Smoke: scripts/smoke-hotplug-indexer.ps1
- Ops log: specs/004-deferred-phase2-closure/ops-signoff-log.md (US6)

Условия ADR (кратко):
1. Зависимости compile-time без циклов
2. Интеграция только через NATS contracts (docs/NATS_SUBJECTS_INTEROP.md)
3. core-api graceful degradation при отсутствии worker
4. Observability + smoke parity
5. Scope ограничен feature + ADR approval

Подтвердите, пожалуйста, что ваша роль согласна с rollout indexer как hot-plug worker на stage/prod.

После трёх подтверждений инженерия выполнит:

  .\scripts\apply-hotplug-signoff.ps1 `
    -ArchitectureOwner "<ФИО>" `
    -ProductOwner "<ФИО>" `
    -OpsSre "<ФИО>"

С уважением,
<команда>
```

## Checklist before sending

- [ ] ADR status still `proposed` (not pre-signed)
- [ ] `smoke-hotplug-indexer.ps1` green on target env
- [ ] Runbook / metrics for indexer process documented in ADR
- [ ] Real names agreed (no placeholders in `apply-hotplug-signoff.ps1`)
