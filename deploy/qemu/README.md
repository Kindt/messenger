# QEMU: две ВМ (dev-сервер + веб-клиент)

Две виртуальные машины Ubuntu 24.04 (cloud image) в одной виртуальной LAN **192.168.76.0/24**:

| ВМ | IP | Роль | Порты на хосте Windows |
|----|-----|------|------------------------|
| `korus-server` | 192.168.76.10 | Docker + **Ansible** `qemu-server-local` | 18080→8080, 18082→8082, 18081→8081 |
| `korus-web` | 192.168.76.20 | Docker + **Ansible** `qemu-web-local` | 19088→9088 |

Репозиторий попадает в гости через **HTTP snapshot** с хоста: `http://10.0.2.2:18890/repo.tgz` → `/mnt/korus`.

## Ansible (spec 003)

Bootstrap и redeploy внутри ВМ — **`deploy/qemu/vm-bootstrap/run-ansible-local.sh`**, который вызывает playbooks из **`deploy/ansible/`**:

| Гость | Playbook | Inventory |
|-------|----------|-----------|
| server | `playbooks/qemu-server-local.yml` | `inventory/qemu/localhost.yml` |
| web | `playbooks/qemu-web-local.yml` | `inventory/qemu/localhost.yml` + `korus_qemu_host_lan_ip` (LAN IP Windows для WS в браузере) |

Между ВМ: API **`http://192.168.76.10:8080`**, ws-gateway **`192.168.76.10:8082`**.  
Браузер на Windows: API **`http://127.0.0.1:18080`**, UI **`http://127.0.0.1:19088`**, WS **`ws://<host-lan-ip>:19088/ws`**.

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

Установить только QEMU (без ВМ): `.\scripts\qemu-up.ps1 -InstallQemuOnly`

Остановка:

```powershell
.\scripts\qemu-down.ps1
```

**Co-hosted VMs:** на одном хосте могут работать QEMU ВМ других проектов. `qemu-down`, auto-remediate и orphan-sweep трогают **только** процессы с `korus-server` / `korus-web` или qcow2 из `deploy/qemu/images/` — не все `qemu-system-x86_64`.

## URL после подъёма

- API (через проброс с server VM): http://127.0.0.1:18080/api/v1/health  
- UI (через web VM): http://127.0.0.1:19088/  
- Acceptance smokes (на хосте с Docker или через SSH-туннели): см. `specs/003-docker-ansible-autotest/quickstart.md`

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

После **одного** полного `-WebOnly` (образ `korus-messenger-web-client:local` на госте):

```powershell
# Включить bind-mount webui с хоста (один раз)
.\scripts\qemu-web-hotswap.ps1 -Enable

# Итерация: правки в modules/web-client/.../webui/ -> sync -> F5
.\scripts\qemu-web-sync.ps1

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

См. также: [`deploy/ansible/README.md`](../ansible/README.md), [`specs/003-docker-ansible-autotest/quickstart.md`](../../specs/003-docker-ansible-autotest/quickstart.md).
