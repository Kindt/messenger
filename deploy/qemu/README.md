# QEMU: две ВМ (dev-сервер + веб-клиент)

Две виртуальные машины Debian 12 (cloud image) в одной виртуальной LAN **192.168.76.0/24**:

| ВМ | IP | Роль | Порты на хосте Windows |
|----|-----|------|------------------------|
| `korus-server` | 192.168.76.10 | Docker + `server-host-up` | 18080→8080, 18082→8082, 18081→8081 |
| `korus-web` | 192.168.76.20 | Docker + `web-host-up` / hot-swap | 19088→9088 |

Репозиторий с хоста доступен в гостях через **SMB slirp** QEMU: `//10.0.2.4/qemu` → `/mnt/korus`.

## Требования

- **QEMU** 8+ (Windows: winget `SoftwareFreedomConservancy.QEMU` или `deploy/qemu/install-qemu.ps1`)
- **Python 3** + **pycdlib** (для ISO cloud-init; при первом запуске: `python -m pip install pycdlib`)
- **~4 ГБ RAM** свободно (по 2 ГБ на ВМ)
- **Виртуализация**: WHPX (Windows) — скрипт включает `-accel whpx`; при ошибке удалите `-accel whpx` в `lib/Start-KorusVm.ps1` (будет TCG, медленно)
- Первый запуск скачивает образ Debian cloud (~300 МБ) и собирает Docker-стеки **внутри ВМ** (долго)

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
- Внутри виртуальной LAN веб→API: `deploy/two-host/web.env.example` с IP **192.168.76.10**

Логи cloud-init: `deploy\qemu\run\server-serial.log`, `web-serial.log`

## Устранение неполадок

1. **QEMU не найден** — запустите PowerShell **от администратора**:  
   `.\deploy\qemu\install-qemu.ps1`  
   или: `winget install -e SoftwareFreedomConservancy.QEMU`
2. **WHPX failed** — в BIOS включите виртуализацию; обновите Windows.
3. **Docker внутри ВМ** — смотрите serial-лог; перезапуск: `.\scripts\qemu-down.ps1` затем `.\scripts\qemu-up.ps1 -Build`
