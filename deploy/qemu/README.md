# QEMU: две ВМ (dev-сервер + веб-клиент)

> **Профили стендов:** QEMU **dev/full**, deploy **pilot/standard**, compose **full-server / pilot / dev-min** — см. канонический справочник [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md).

Две виртуальные машины Ubuntu 24.04 (cloud image) в одной виртуальной LAN **192.168.76.0/24**:

| ВМ | IP | Роль | Порты на хосте Windows |
|----|-----|------|------------------------|
| `korus-server` | 192.168.76.10 | Docker + **Ansible** `qemu-server-local` | 18080→8080, 18082→8082, 18081→8081, **17880→7880** (LiveKit L2) |
| `korus-web` | 192.168.76.20 | Docker + **Ansible** `qemu-web-local` | 19088→9088 |

Репозиторий попадает в гости через **HTTP snapshot** с хоста: `http://10.0.2.2:18890/repo.tgz` → `/mnt/korus`.

## Ansible (spec 003)

Bootstrap и redeploy внутри ВМ — **`deploy/qemu/vm-bootstrap/run-ansible-local.sh`**, который вызывает playbooks из **`deploy/ansible/`**:

| Гость | Playbook | Inventory |
|-------|----------|-----------|
| server | `playbooks/qemu-server-local.yml` | `inventory/qemu/localhost.yml` |
| web | `playbooks/qemu-web-local.yml` | `inventory/qemu/localhost.yml` + `korus_qemu_host_lan_ip` (LAN IP Windows для WS в браузере) |

Между ВМ: API **`http://192.168.76.10:8080`**, ws-gateway **`192.168.76.10:8082`**.  
Браузер на Windows: API **`http://127.0.0.1:18080`**, UI **`http://127.0.0.1:19088`**, WS **`ws://<host-lan-ip>:19088/ws`**, LiveKit **`ws://127.0.0.1:17880`** (spec 013 L2, после redeploy с `livekit` в full-server).

Ручной redeploy без сброса дисков:

```powershell
.\scripts\qemu-redeploy.ps1
```

## Требования

- **QEMU** 8+ (Windows: winget `SoftwareFreedomConservancy.QEMU` или `deploy/qemu/install-qemu.ps1`)
- **Python 3** + **pycdlib** (для ISO cloud-init; при первом запуске: `python -m pip install pycdlib`)
- **~13 ГБ RAM** на хост (см. `RESOURCES.md`)
- **Виртуализация**: WHPX (Windows) — скрипт включает `-accel whpx`; при ошибке см. `lib/Start-KorusVm.ps1`
- **Cloud image Ubuntu 24.04** — **не хранится в git** (~250 МБ). При первом `qemu-up` скачивается автоматически; вручную: `.\scripts\ensure-qemu-images.ps1` (см. [`images/README.md`](images/README.md))
- Первый запуск собирает Docker-стеки **внутри ВМ** через Ansible (долго)

## Запуск

Из корня репозитория:

```powershell
# Рекомендуется для отладки: GTK-окна ВМ + live-монитор в отдельном окне
.\scripts\qemu-dev-up.ps1

# Или вручную:
.\scripts\qemu-up.ps1 -Graphical
.\scripts\qemu-watch.ps1 -NewWindow
```

Headless (как раньше): `.\scripts\qemu-up.ps1` или `$env:KORUS_QEMU_DISPLAY=none`

## Полный стенд + hotswap (рекомендуется)

На **Windows-хосте** Docker не запускается. Полный стек — **внутри двух VM**:

| Слой | Гость | Что поднимается |
|------|-------|-----------------|
| Server | `korus-server` | `full-stack-up.sh` — все контейнеры `docker-compose.full-server.yml` |
| Web | `korus-web` | **web-a + web-b + web-dev** (bind-mount) + **lb** → браузер на `web-dev` |

```powershell
# Полный стенд + hotswap (диски сохраняются)
.\scripts\qemu-full-stack-up.ps1

# Пересборка образов в VM (~20-90 мин)
.\scripts\qemu-full-stack-up.ps1 -Rebuild

# Без hotswap (lb только web-a/web-b)
.\scripts\qemu-full-stack-up.ps1 -SkipHotswap

.\scripts\qemu-full-stack-down.ps1
```

UI sync после подъёма: `.\scripts\qemu-dev-mode.ps1 -Mode sync-ui` (~10 с).

**Не путать** с `scripts/full-stack-up.ps1` на хосте — только Linux/CI (на Windows dev-хосте запрещён).

## Dev-цикл (только UI sync)

Если стек уже через `qemu-full-stack-up.ps1`, hotswap уже включён. См. также «Golden path» ниже — `sync-ui`, Playwright.

Установить только QEMU (без ВМ): `.\scripts\qemu-up.ps1 -InstallQemuOnly`

Остановка:

```powershell
.\scripts\qemu-down.ps1
```

**Co-hosted VMs:** на одном хосте могут работать QEMU ВМ других проектов. `qemu-down`, auto-remediate и orphan-sweep трогают **только** процессы с `korus-server` / `korus-web` или qcow2 из `deploy/qemu/images/` — не все `qemu-system-x86_64`.

## URL после подъёма

- API (через проброс с server VM): http://127.0.0.1:18080/api/v1/health  
- UI (через web VM): http://127.0.0.1:19088/  
- Acceptance smokes (на хосте с Docker или через SSH-туннели): см. `deploy/ansible/DEPLOY_QUICKSTART.md`

## Справочник стендов

Краткая карта команд ниже. **Смысл профилей** (dev ≠ pilot, full-server vs product tier) — в [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md).

На Windows dev-хосте runtime — **только QEMU**; `full-stack-up.ps1` на хосте Windows **не использовать** (см. `.cursor/rules/qemu-host-isolation.mdc`).

### Windows QEMU (две VM, профили дисков)

**Профили** (`deploy/qemu/run/stack-profile.txt`): `dev` (ежедневная работа) и `full` (acceptance / outer gate). Диски раздельные: `server-dev` / `web-dev` vs `server-full` / `web-full`. Одновременно может работать только один профиль (общие порты 18080/19088). Переключение: `qemu-down`, затем нужная команда.

| Стенд | Команда на хосте | Server VM | Web VM | Hotswap UI | Диски |
|-------|------------------|-----------|--------|------------|-------|
| **Dev warm** | `qemu-dev-mode.ps1 -Mode warm` | на диске dev | на диске dev | как было | `server-dev`, `web-dev` |
| **Full + hotswap** | `qemu-full-stack-up.ps1` | `full-stack-up.sh` — `docker-compose.full-server.yml` | `docker-compose.yml` + overlay → a/b/dev/lb | да | `server-full`, `web-full` |
| **Full без hotswap** | `qemu-full-stack-up.ps1 -SkipHotswap` | то же | a/b/lb | нет | full profile |
| **Sync API / Web** | `qemu-dev-mode.ps1 -Mode sync-api` / `sync-web` | Ansible sync | Ansible sync | не меняет | активный профиль |
| **UI sync** | `qemu-dev-mode.ps1 -Mode sync-ui` | — | `webui.tgz` → overlay | нужен hotswap | активный профиль |
| **Cold dev wipe** | `qemu-up` без `-KeepDisks` (profile dev) | cloud-init с нуля | то же | — | **только dev qcow2** |
| **Cold full wipe** | `qemu-full-stack-up.ps1 -FreshDisks` | cloud-init с нуля | то же | после up — hotswap | **только full qcow2** |
| **Outer gate** | `qemu-plan-orchestrator.ps1 -SkipVmUp` | smoke + Playwright | — | — | full profile |

Compose web hotswap на QEMU: merge `docker-compose.yml` + `docker-compose.qemu-hotswap-overlay.yml` (web-dev + lb→dev; web-a/b из base). `sync-ui` одинаков для dev и full.

### Docker на хосте (Linux / CI / two-host)

| Стенд | Compose / скрипт | Backend | Web UI | Отличие от full-server |
|-------|------------------|---------|--------|------------------------|
| **Full server** | `full-stack-up.ps1` / `.sh` → `docker-compose.full-server.yml` | PG×2, Redis, NATS, MinIO, Solr, Keycloak, core-api, ws-gateway | отдельно: `korus-web-up` | все воркеры: message-pipeline, archiver, deep-archiver, indexer, push, export-replay, retention |
| **Dev-min infra** | `dev-infra-up.ps1` | только инфра | — | без Java-сервисов |
| **Dev-min + web** | `dev-web-stack-up.ps1` (profile `web`) | + ws-gateway, message-pipeline, push, retention | опционально attach | **без** archiver, deep-archiver, indexer, export-replay |
| **Korus-web standalone** | `korus-web-up.ps1` | API через `host.docker.internal:8080` | `web-a` + `web-b` + `lb` :9088 | — |
| **Korus-web attach** | `korus-web-up.ps1 -Attach` | Docker-сеть dev-min, `core-api:8080` | то же | — |
| **Host hotswap** | `dev-overlay-up.ps1` → `docker-compose.hotswap.yml` | внешний API | один `web-dev` :9088, bind `dev-overlay/webui` | без lb |
| **Export smoke** | `full-stack-up -ExportSmoke` + overlays | full-server + export overlays | — | compliance smokes |
| **Observability** | Ansible `observability-only.yml` | Prometheus + Grafana | — | addon |

Two-host на Windows вручную: `server-host-up.ps1` + `web-host-up.ps1`. На Linux — Ansible `site.yml`.

### Ansible inventories (куда деплоится код)

| Inventory | Топология | TLS | Playbook / стек | Назначение |
|-----------|-----------|-----|-----------------|------------|
| `inventory/qemu/` | 2 VM | нет | `qemu-server-local` → `full-stack-up.sh` или `pilot-stack-up.sh` (`korus_deploy_profile`); `qemu-web-local` → `korus-web-up.sh` | Windows QEMU |
| `inventory/local/` | 1 node | нет | `ci-local.yml` | CI, Linux all-in-one |
| `inventory/two-host/` | server + web LAN | опционально | `site.yml` | два Linux-хоста |
| `inventory/stage/` | two-host | да (LE/BYO) | `site.yml` + role `tls` | pre-prod |
| `inventory/prod/` | two-host | да + vault | `site.yml` | production scaffold |

Подробнее: [`deploy/ansible/README.md`](../ansible/README.md), [`deploy/two-host/README.md`](../two-host/README.md).

### Web UI — варианты compose

| Файл | Контейнеры | LB → upstream | Bind-mount webui |
|------|------------|---------------|------------------|
| `korus-web/docker-compose.yml` | web-a, web-b, lb | a + b (least_conn) | нет |
| `docker-compose.qemu-hotswap-overlay.yml` | overlay к `.yml`: +web-dev, lb→dev | web-dev | да (`/overlay/webui`) |
| `docker-compose.qemu-full-hotswap.yml` | deprecated duplicate | web-dev | да (legacy guests) |
| `docker-compose.hotswap-qemu.yml` | web-dev, lb | web-dev | да (минимальный) |
| `docker-compose.hotswap.yml` | web-dev | нет (порт 9088) | `dev-overlay/webui` |
| `docker-compose.attach.yml` | overlay к `.yml` | — | API через сеть dev-min |
| `docker-compose.turn.yml` | overlay | — | + coturn для RTC |

### Что выбрать

| Задача | Стенд |
|--------|-------|
| Ежедневная работа на Windows | `qemu-dev-mode.ps1 -Mode warm` (+ sync-ui) |
| Full stack / Playwright outer gate | `qemu-full-stack-up.ps1` |
| CI / Linux all-in-one | `ansible-playbook … ci-local.yml` или `full-stack-up.sh` |
| Два сервера в LAN | `ansible-playbook … site.yml` + `two-host` / `stage` / `prod` |

## Golden path (web-client dev + Playwright)

```powershell
# 1) Поднять ВМ (первый раз долго: cloud-init + Ansible + docker build в гостях)
.\scripts\qemu-dev-up.ps1
# или headless: .\scripts\qemu-up.ps1

# 2) Обновить код в гостях без сброса дисков (sync = без docker build)
.\scripts\qemu-dev-mode.ps1 -Mode status
.\scripts\qemu-dev-mode.ps1 -Mode sync-api-core # Java/API only ~3 min
.\scripts\qemu-dev-mode.ps1 -Mode sync-api      # Ansible server (no image build)
.\scripts\qemu-dev-mode.ps1 -Mode sync-web      # web Tomcat ~3–8 мин
.\scripts\qemu-redeploy.ps1 -ServerOnly -Rebuild   # только Dockerfile/Gradle (20–90 мин)

### Быстрый цикл UI (hot-swap, ~10 с вместо ~15–25 мин redeploy)

После `qemu-full-stack-up.ps1` hotswap уже включён (`web-a`/`web-b` остаются up, lb → `web-dev`). Или вручную: `qemu-web-hotswap.ps1 -Enable`.

```powershell
# Включить bind-mount webui + lb (один раз; подтягивает repo на web guest)
.\scripts\qemu-web-hotswap.ps1 -Enable

# Итерация: правки webui или webui-build/locales/messages -> sync-ui -> F5
.\scripts\qemu-dev-mode.ps1 -Mode sync-ui

# Playwright с автосинком UI перед тестом
.\scripts\playwright-dev-loop.ps1 -Tier ui-auth -SyncWebUi
```

`webui.tgz` (~сотни KiB) вместо `repo.tgz` (~180 MiB); контейнер не пересобирается.

| Сценарий | Команда | Время |
|----------|---------|-------|
| Статус / фаза guest | `qemu-dev-mode.ps1 -Mode status` | сек |
| Warm boot | `qemu-dev-mode.ps1 -Mode warm` | 2–8 мин |
| API / backend (sync) | `qemu-dev-mode.ps1 -Mode sync-api` | ~3–8 мин |
| API core-api only (Java/Flyway) | `qemu-dev-mode.ps1 -Mode sync-api-core` | ~3–5 мин |
| API full rebuild | `qemu-dev-mode.ps1 -Mode rebuild-api` | 20–90 мин |
| Web sync | `qemu-dev-mode.ps1 -Mode sync-web` | ~3–8 мин |
| UI JS/CSS/Tailwind | `qemu-dev-mode.ps1 -Mode sync-ui` | ~5–15 с |
| Включить hotswap | `qemu-web-hotswap.ps1 -Enable` или `-Status` | ~1–3 мин |
| Остановка | `qemu-dev-mode.ps1 -Mode stop` | сек |

Hotswap: **web-a + web-b + web-dev + lb** (compose `qemu-full-hotswap`); браузер на **web-dev**, `/ws` через lb. `sync-ui` собирает **tailwind + locales** (`build:assets`) перед `webui.tgz`.

**Backup дисков:** `qemu-down` → `qemu-backup.ps1 -Label green-stack` → `qemu-up -KeepDisks`. Restore: `qemu-restore.ps1 -From deploy\qemu\backups\<dir>`.

**Git push (GitHub):** `.\scripts\git-push.ps1` (обход corp proxy).

# 3) Дождаться стека (опционально)
.\scripts\qemu-stack-wait.ps1

# 4) Smokes с хоста
.\scripts\smoke-korus-web.ps1 -WebBaseUrl http://127.0.0.1:19088 -CheckApi
# Optional: assert wsUrl contains Windows LAN IP (see deploy/qemu/lib/Get-KorusLanHostIp.ps1)
.\scripts\smoke-korus-web.ps1 -WebBaseUrl http://127.0.0.1:19088 -ExpectWsHost 192.168.x.x

# 5) Inner loop (US9) then outer Playwright gate
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
# Outer gate (full suite once):
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp

# Or manual full suite:
cd tests\e2e-web
npm ci
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
$env:KORUS_API_URL = "http://127.0.0.1:18080"
npx playwright test
```

**First boot** (before `qemu-up` or once per fresh disks): preload Docker images to avoid long pulls:

```powershell
.\scripts\preload-qemu-docker-images.ps1
```

Поминутные отчёты в чат + auto-fix/restart: `.\scripts\qemu-chat-watch.ps1` (loop → `AGENT_LOOP_TICK_qemu_chat`). Полный план (stack → smoke → Playwright → gate report): `.\scripts\qemu-plan-orchestrator.ps1` (`AGENT_LOOP_TICK_qemu_plan`). При активном docker pull/gradle/build auto-remediate **ждёт**, не redeploy/restart. Stop: `.\scripts\stop-qemu-plan-orchestrator.ps1`.

Логи: `deploy\qemu\run\server-serial.log`, `web-serial.log`; в гостях: **`/var/log/korus-bootstrap.log`**

### Визуальный мониторинг и отладка

| Команда | Назначение |
|---------|------------|
| `.\scripts\qemu-dev-up.ps1` | GTK-окна обеих ВМ + `qemu-watch` в новом окне |
| `.\scripts\qemu-watch.ps1 -NewWindow` | Live dashboard: serial, bootstrap, docker, health |
| `.\scripts\qemu-logs.ps1` | Снимок состояния (serial + SSH bootstrap + health) |
| `.\scripts\qemu-logs.ps1 -Follow` | Tail `server-serial.log` |
| `.\scripts\qemu-up.ps1 -Graphical` | Только поднять ВМ с GTK (`-Display gtk\|sdl\|default`) |
| `$env:KORUS_QEMU_DISPLAY=gtk` | То же для любого вызова `qemu-up` |

При `-Graphical` / `gtk`: если GTK недоступен, `Start-KorusVm` автоматически пробует `sdl`, затем `default`.

**Ansible `skipping` на server — норма для QEMU** (скрыто: `display_skipped_hosts = False`). В GTK — live-лог как `docker compose build` / `gradle --console=plain`: `Step N/M`, `> Task :...`, `| container | ...`.

В GTK-окне: загрузка ядра, затем **live docker/gradle build** вместо login prompt. Если видите `login:` — `.\scripts\qemu-console-on.ps1`.

| `.\scripts\qemu-console-on.ps1` | Включить вывод bootstrap в уже запущенных GTK-окнах |

## Устранение неполадок

1. **QEMU не найден** — PowerShell **от администратора**: `.\deploy\qemu\install-qemu.ps1`
2. **WHPX failed** — `.\scripts\qemu-fast-up.ps1` (elevated) или `.\deploy\qemu\enable-fast-mode.ps1`. По умолчанию: `-accel whpx,kernel-irqchip=off -cpu qemu64`. Принудительно TCG: `$env:KORUS_QEMU_FORCE_TCG='1'`.
3. **«VM падает» через 5–15 мин** — часто не WHPX, а обрыв `repo.tgz` (redeploy/restart HTTP) или watchdog до фиксов в ветке 004. Не запускайте `qemu-redeploy` пока cloud-init не завершился; используйте `qemu-stack-wait.ps1`. Предупреждение `Failed to get performance monitoring features` в whpx-probe.err — **не ошибка**.
4. **Нет cloud image** — `.\scripts\ensure-qemu-images.ps1` или повторный `.\scripts\qemu-up.ps1` (скачивание при отсутствии `.img`).
5. **Ansible/pip в ВМ** — cloud-init ставит `python3-pip`; bootstrap ставит `ansible` через pip. Смотрите `/var/log/korus-bootstrap.log`.
6. **Пересборка** — `.\scripts\qemu-down.ps1` затем `.\scripts\qemu-up.ps1` или `.\scripts\qemu-redeploy.ps1 -KeepDisks` не сбрасывает диски: `qemu-up.ps1 -KeepDisks`.
7. **nginx lb logs** — в образе `nginx:alpine` файлы `/var/log/nginx/*.log` — симлинки на stdout/stderr; **`docker exec … tail …/error.log` зависает**. Смотрите `docker logs korus-web-lb-1` на web-госте или `.\scripts\qemu-logs.ps1` (секция `nginx lb`). После правок шаблона lb: `.\scripts\qemu-redeploy.ps1 -WebOnly -Force -Rebuild`.

См. также: [`deploy/ansible/README.md`](../ansible/README.md), [`deploy/ansible/DEPLOY_QUICKSTART.md`](../../deploy/ansible/DEPLOY_QUICKSTART.md).
