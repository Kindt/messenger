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
.\scripts\qemu-up.ps1
```

Только установить QEMU в `deploy\qemu\tools\qemu` (без ВМ):

```powershell
.\scripts\qemu-up.ps1 -InstallQemuOnly
```

Остановка:

```powershell
.\scripts\qemu-down.ps1
```

## URL после подъёма

- API (через проброс с server VM): http://127.0.0.1:18080/api/v1/health  
- UI (через web VM): http://127.0.0.1:19088/  
- Acceptance smokes (на хосте с Docker или через SSH-туннели): см. `specs/003-docker-ansible-autotest/quickstart.md`

Логи: `deploy\qemu\run\server-serial.log`, `web-serial.log`; в гостях: **`/var/log/korus-bootstrap.log`**

## Устранение неполадок

1. **QEMU не найден** — PowerShell **от администратора**: `.\deploy\qemu\install-qemu.ps1`
2. **WHPX failed** — включите виртуализацию в BIOS.
3. **Нет cloud image** — `.\scripts\ensure-qemu-images.ps1` или повторный `.\scripts\qemu-up.ps1` (скачивание при отсутствии `.img`).
4. **Ansible/pip в ВМ** — cloud-init ставит `python3-pip`; bootstrap ставит `ansible` через pip. Смотрите `/var/log/korus-bootstrap.log`.
5. **Пересборка** — `.\scripts\qemu-down.ps1` затем `.\scripts\qemu-up.ps1` или `.\scripts\qemu-redeploy.ps1 -KeepDisks` не сбрасывает диски: `qemu-up.ps1 -KeepDisks`.

См. также: [`deploy/ansible/README.md`](../ansible/README.md), [`specs/003-docker-ansible-autotest/quickstart.md`](../../specs/003-docker-ansible-autotest/quickstart.md).
