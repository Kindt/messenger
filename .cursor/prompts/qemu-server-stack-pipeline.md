# QEMU server stack — 2 агента (server + web)

**Скопируй всё ниже** или получи актуальный блок:

```powershell
.\scripts\Start-KorusServerStack.ps1 -EmitPrompt -Agent both
# Server-only:
.\scripts\Start-KorusServerStack.ps1 -EmitPrompt -Agent server
# Web-only:
.\scripts\Start-KorusServerStack.ps1 -EmitPrompt -Agent web
```

Статус: `deploy/qemu/run/server-stack-status.json`

---

## Контекст

| VM | Роль | Host ports | Агент |
|----|------|------------|-------|
| `korus-server` | Docker full-server + Ansible | `:18080` API | **server** |
| `korus-web` | korus-web + hotswap overlay | `:19088` UI | **web** |

Профиль **dev** (`server-dev.qcow2`, `web-dev.qcow2`). WHPX обязателен; TCG запрещён. Runtime только в guest.

---

<!-- AGENT-INJECT:START -->
# Korus server stack -- agent: server

Status: API=200 UI=200 ready=True
Profile: dev (server-dev + web-dev), WHPX required, host Docker forbidden.

## Role
**Server agent** -- QEMU server VM, API :18080, core-api, workers, remediate.

### Commands (lightest to heaviest)
```powershell
.\scripts\Start-KorusServerStack.ps1 -Mode status
.\scripts\Start-KorusServerStack.ps1 -Mode fast
.\scripts\Start-KorusServerStack.ps1 -Mode warm
.\scripts\Start-KorusServerStack.ps1 -Mode sync-api
.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-core
.\scripts\qemu-guest-job.ps1 -Loop
.\scripts\Start-KorusServerStack.ps1 -Mode enable-api-hotswap
.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-hotswap
.\scripts\Start-KorusServerStack.ps1 -Mode remediate
.\scripts\Start-KorusServerStack.ps1 -Mode fix-api
.\scripts\Start-KorusServerStack.ps1 -Mode rebuild-api -Force
```

Success: curl http://127.0.0.1:18080/api/v1/health -> 200, /ready database_ok=true
<!-- AGENT-INJECT:END -->

---

## Матрица режимов (от холодного к hotswap)

| Tier | Режим | Команда | Время | Когда |
|------|-------|---------|-------|-------|
| 0 | stop | `Start-KorusServerStack -Mode stop` | сек | Явная остановка |
| 1 | cold | `-Mode cold` | часы | Новые диски, первый bootstrap |
| 1b | cold+wipe | `-Mode cold -FreshDisks` | часы | Сброс dev-дисков |
| 2 | fast | `-Mode fast` | 2–5 мин | WHPX restart KeepDisks |
| 3 | warm | `-Mode warm` | 5–15 мин | **Ежедневный старт** |
| 3 | wait | `-Mode wait -RequireReady` | varies | Цикл/VPP stack prep |
| 4 | remediate | `-Mode remediate` | 5–15 мин | API down после failed rebuild |
| 4 | fix-api | `-Mode fix-api` | 2–5 мин | Быстрый recreate core-api |
| 5 | sync-api | `-Mode sync-api` | 5–10 мин | Java/backend без Dockerfile |
| 6 | sync-api-core | `-Mode sync-api-core` | 5–15 мин | Flyway / core-api image |
| 7 | enable-api-hotswap | `-Mode enable-api-hotswap` | 1–2 мин | Один раз перед Java loop |
| 8 | sync-api-hotswap | `-Mode sync-api-hotswap` | 1–3 мин | **Java-only цикл** |
| 5 | sync-web | `-Mode sync-web` | 5–10 мин | web-client без static |
| 9 | sync-ui | `-Mode sync-ui` | сек | JS/CSS/Tailwind |
| 7 | enable-web-hotswap | `-Mode enable-web-hotswap` | 1–3 мин | lb → web-dev |
| 10 | rebuild-api | `-Mode rebuild-api -Force` | 20–90 мин | Dockerfile / Gradle deps |
| 10 | rebuild-web | `-Mode rebuild-web -Force` | 20–60 мин | Web Dockerfile |
| — | full | `-Mode full` | varies | Профиль full (отдельные диски) |
| — | monitored | `-Mode monitored` | varies | Авто redeploy + remediate |

Фасад-алиас: `.\scripts\qemu-dev-mode.ps1 -Mode <mode>` (те же режимы для lifecycle).

---

## Workflow: 2 агента

### Orchestrator (оба)

1. `.\scripts\Start-KorusServerStack.ps1 -Mode status`
2. Если VM down → `-Mode warm` (или `-Mode fast` если только WHPX/TCG)
3. Дождаться API **и** UI 200
4. Разделить работу по агентам ниже

### Agent: server (API)

**Precondition:** SSH `:12221`, VM running.

```powershell
.\scripts\Start-KorusServerStack.ps1 -Mode status
# API 000:
.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-core
.\scripts\qemu-guest-job.ps1 -Loop          # poll каждые 3 мин, не блокировать IDE
# всё ещё 000:
.\scripts\Start-KorusServerStack.ps1 -Mode remediate
.\scripts\Start-KorusServerStack.ps1 -Mode fix-api
```

**Java dev loop** (после `-Mode enable-api-hotswap` один раз):

```powershell
# edit modules/core-api/**/*.java
.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-hotswap
curl.exe -s http://127.0.0.1:18080/api/v1/health
```

### Agent: web (UI)

**Precondition:** API healthy (server agent).

```powershell
.\scripts\Start-KorusServerStack.ps1 -Mode sync-web      # web-client
.\scripts\Start-KorusServerStack.ps1 -Mode enable-web-hotswap  # один раз
.\scripts\Start-KorusServerStack.ps1 -Mode sync-ui       # webui static
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:19088/
```

---

## Диагностика

```powershell
.\scripts\qemu-fast-up.ps1 -ProbeOnly
.\scripts\qemu-guest-job.ps1
.\scripts\qemu-status-minute.ps1 -Once
```

Логи: `deploy/qemu/run/*-serial.log`, `lab-stack-wait.log`, `status-minute.log`

---

## Запрещено

- Host Docker / `docker compose` на Windows
- `KORUS_QEMU_FORCE_TCG` / TCG fallback
- Блокирующий `qemu-sync-api-core -Wait` в IDE без явной просьбы
- Убивать guest job без чтения `/tmp/korus-job-*.log`

---

## Успех

- `http://127.0.0.1:18080/api/v1/health` → **200**
- `http://127.0.0.1:18080/api/v1/health/ready` → `database_ok: true`
- `http://127.0.0.1:19088/` → **200**

Следующий шаг: VPP, Playwright tier, smoke, или целевой feature-цикл.
