# QEMU dev modes: стабилизация сборки, redeploy и hotswap

**Дата:** 2026-06-12  
**Статус:** `implemented` (2026-06-12)  
**Приоритет:** D — волны A → B → C  
**Связано:** `deploy/qemu/README.md`, `.cursor/rules/qemu-redeploy-monitor.mdc`, spec 003/004

---

## 1. Проблема (анализ 2026-06-12)

### Симптомы

| Симптом | Наблюдение |
|---------|------------|
| API 000, UI 200 после `qemu-up -KeepDisks` | Server containers не подняты; web — со старого диска |
| `qemu-redeploy -ServerOnly` 90+ мин / fail | Guest: `docker compose --build` + Gradle (`archiver-worker`) |
| VM death на хосте | `qemu-system-x86_64` exit; WHPX, ~3–12 мин idle или mid-build |
| Hotswap не использовался | Цепочка не доходила до `WebOnly` / `-Enable` |
| Конфликт режимов | Параллельные redeploy, auto-remediate, monitored без единой матрицы |

### Корневая причина

**Нет градации «лёгкий sync» vs «тяжёлый rebuild».**  
`qemu-redeploy.ps1` всегда `KORUS_BUILD=1` → полная пересборка 14 сервисов в VM 10 ГБ.

### Цель

1. **A — Надёжность:** предсказуемый подъём стека; не ломать ready stack.  
2. **B — Скорость:** UI секунды (hotswap), API минуты (sync без build).  
3. **C — DX агента:** один фасад + явные правила в AGENTS/rules.

---

## 2. Матрица режимов (целевая)

| Режим | Команда (целевая) | KORUS_BUILD | Время | Когда |
|-------|-------------------|-------------|-------|-------|
| **cold** | `qemu-up` (без KeepDisks) | 1 | часы (первый раз) | Новый диск, смена ОС |
| **warm** | `qemu-up -KeepDisks` + `qemu-stack-wait` | 0 (compose up) | 2–8 мин | Ежедневный старт |
| **sync-api** | `qemu-redeploy -ServerOnly` (default) | 0 | 3–8 мин | Правки Java/backend |
| **sync-web** | `qemu-redeploy -WebOnly` (default) | 0 | 3–8 мин | Правки web-client (без static) |
| **sync-ui** | `qemu-web-sync` | — | 5–15 с | JS/CSS/Tailwind в webui |
| **hotswap** | `qemu-web-hotswap -Enable` | — | 1–3 мин | Один раз после sync-web |
| **rebuild** | `qemu-redeploy -Rebuild` | 1 | 20–90 мин | Dockerfile, Gradle deps, cold |
| **stop** | `qemu-down` / `qemu-dev-mode -Mode stop` | — | сек | Явная остановка |

**Правило:** без `-Rebuild` — **никогда** `docker compose --build` на server/web.

---

## 3. Волна A — Надёжность

### A1. Default sync в redeploy

**Файлы:** `scripts/qemu-redeploy.ps1`, `deploy/qemu/vm-bootstrap/run-ansible-local.sh` (без изменений — уже `BUILD="${KORUS_BUILD:-0}"`).

- Добавить `-Rebuild` switch → `KORUS_BUILD=1` в guest nohup.
- Default (без флага): `KORUS_BUILD=0` → `korus_build_images=false`.
- Лог: `redeploy mode=sync|rebuild role=server|web`.

### A2. Preflight «stack ready → skip»

**Файлы:** `scripts/qemu-redeploy.ps1`, `scripts/qemu-redeploy-monitored.ps1`.

Перед repo pack:

1. VM alive (`Test-KorusQemuStackRunning`).
2. SSH 12221/12222 ready; settle 60–90 с после fresh `qemu-up`.
3. Если target health OK (`/api/v1/health` + `/ready` для server, `:19088/` для web) и не `-Force` → skip с `[OK] already ready`.

### A3. WHPX → TCG fallback

**Файлы:** `scripts/qemu-redeploy-monitored.ps1`, `deploy/qemu/lib/Start-KorusVm.ps1` (уже `KORUS_QEMU_FORCE_TCG`).

- При `VM died` в первые 15 мин цикла: следующий `qemu-up` с `$env:KORUS_QEMU_FORCE_TCG='1'`.
- Лог в `redeploy-monitored.log`: `fallback=tcg`.

### A4. Auto-remediate vs manual

**Файлы:** `deploy/qemu/lib/Invoke-KorusQemuAutoRemediate.ps1`.

- Уже: `golden-path.no-auto-restart`, `redeploy lock active`.
- Добавить: не spawn `qemu-auto-restart` если файл `golden-path` моложе 120 мин.
- Документировать в README: при ручном redeploy monitored ставит golden-path.

### A5. Прогресс в wait-loop

**Файлы:** `deploy/qemu/lib/Get-KorusGuestBootstrapPhase.ps1` (новый), `scripts/qemu-redeploy.ps1`.

- Каждые 60 с: tail guest bootstrap via plink (если SSH up).
- Фазы: `repo-sync | docker-pull | gradle-build | compose-up | ready | unknown`.
- При VM dead — throw сразу; при gradle-build >45 мин без смены fingerprint — warn (не kill).

---

## 4. Волна B — Скорость итерации

### B1. Hotswap hardening

**Файлы:** `scripts/qemu-web-hotswap.ps1`, `scripts/qemu-web-sync.ps1`.

- `-Status`: mount active, last sync time, `tailwind.css` present on host URL.
- Preflight: образ `korus-messenger-web-client:local` на guest (иначе → `sync-web` first).
- `qemu-web-sync`: опционально `./gradlew :modules:web-client:buildTailwindCss` на хосте перед pack.

### B2. Playwright integration

**Файлы:** `scripts/playwright-dev-loop.ps1`, `tests/e2e-web/README.md`.

- `-SyncWebUi`: если hotswap off → подсказка `qemu-dev-mode -Mode enable-hotswap`.
- Документировать в `deploy/qemu/README.md` golden path: warm → sync-api если API down → enable-hotswap → sync-ui.

### B3. Warm boot path

**Файлы:** `deploy/qemu/cloud-init` (без изменений на KeepDisks), `scripts/qemu-stack-wait.ps1`.

- После `qemu-up -KeepDisks`: рекомендовать `qemu-stack-wait` до SSH; не redeploy пока cloud-init не finished (serial: `cloud-init.*finished`).

---

## 5. Волна C — DX агента

### C1. Фасад `qemu-dev-mode.ps1`

**Новый файл:** `scripts/qemu-dev-mode.ps1`

```powershell
-Mode warm | sync-api | sync-web | sync-ui | rebuild-api | rebuild-web | enable-hotswap | status | stop
-Force   # skip ready preflight
-Rebuild # alias для rebuild-*
```

Делегирует в существующие скрипты; не дублирует Ansible/compose логику.

### C2. Правила агента

**Файлы:** `.cursor/rules/qemu-redeploy-monitor.mdc`, `AGENTS.md`.

Decision tree:

```
status → ready? → sync-ui / playwright
       → API down, UI up? → sync-api (NOT rebuild)
       → both down? → warm → stack-wait → sync-api
       → rebuild → только -Rebuild или явный запрос пользователя
```

### C3. Monitored redeploy update

**Файлы:** `scripts/qemu-redeploy-monitored.ps1`.

- Default target: `sync-api` not rebuild.
- `-Rebuild` passthrough.
- Показывать `Get-KorusGuestBootstrapPhase` в логе каждого цикла.

---

## 6. Error handling

| Ошибка | Действие |
|--------|----------|
| VM died (WHPX) | TCG fallback, retry cycle |
| VM died (TCG too) | STOP, сообщить пользователю (RAM/диск) |
| plink abort | retry 3x (`Update-KorusGuestRepo`); then VM check |
| stale lock | clear if stack down or age >45m |
| stack ready + redeploy requested | skip unless `-Force` |
| gradle stuck >60m | log warn + guest `pgrep`; не auto-restart |
| hotswap without image | redirect to `sync-web` |

---

## 7. Тестирование и критерии приёмки

### Ручные сценарии (Windows host, QEMU)

| # | Сценарий | Критерий |
|---|----------|----------|
| T1 | `warm` после вчерашнего green stack | API+UI 200 <10 мин, без build в bootstrap log |
| T2 | `sync-api` после правки одного `.java` | API 200 <10 мин, нет `docker compose build` в log |
| T3 | `sync-ui` + hotswap | `tailwind.css` 200, правка `app.js` видна после sync <30 с |
| T4 | `-Rebuild` явно | build в log, eventual API ready |
| T5 | `status` при gradle-build | phase=gradle-build в выводе |
| T6 | monitored + VM death | TCG retry, не blind 90m wait |

### Документация

- `deploy/qemu/README.md` — матрица режимов  
- `AGENTS.md` — decision tree  
- `CHANGELOG.md` — при merge волны A1+

---

## 8. План реализации (порядок PR)

| ID | Задача | Файлы | Волна |
|----|--------|-------|-------|
| **A1** | `-Rebuild`; default `KORUS_BUILD=0` | `qemu-redeploy.ps1` | A |
| **A2** | Preflight ready skip + `-Force` | `qemu-redeploy.ps1`, `qemu-redeploy-monitored.ps1` | A |
| **A3** | TCG fallback в monitored | `qemu-redeploy-monitored.ps1` | A |
| **A5** | `Get-KorusGuestBootstrapPhase` + wait-loop | `deploy/qemu/lib/*.ps1`, `qemu-redeploy.ps1` | A |
| **A4** | Auto-remediate golden-path TTL | `Invoke-KorusQemuAutoRemediate.ps1` | A |
| **B1** | hotswap `-Status`, sync tailwind | `qemu-web-hotswap.ps1`, `qemu-web-sync.ps1` | B |
| **B2** | README golden path | `deploy/qemu/README.md` | B |
| **C1** | `qemu-dev-mode.ps1` | `scripts/qemu-dev-mode.ps1` | C |
| **C2** | Rules + AGENTS | `.cursor/rules/`, `AGENTS.md` | C |
| **C3** | Monitored sync-default | `qemu-redeploy-monitored.ps1` | C |

**Рекомендуемый первый PR:** A1 + A2 (максимальный эффект, минимальный diff).

---

## 9. Риски

| Риск | Митигация |
|------|-----------|
| Sync без build при устаревших образах | `-Rebuild`; smoke fail → подсказка rebuild |
| TCG слишком медленный | Только fallback, не default |
| KeepDisks + битые containers | `sync-api` делает compose up; Exited(255) → один rebuild |
| Co-hosted QEMU | `Test-KorusQemuProcess` — без изменений |

---

## 10. Out of scope

- CI/Linux runners (другой pipeline)
- Stage/prod Ansible (spec 003 T080+)
- Уменьшение RAM server VM ниже 10 ГБ (отдельный ADR)
