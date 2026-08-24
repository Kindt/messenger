# Цикл: QEMU lab stack recovery

Скопируй всё ниже при недоступности `:18080` / `:19088`.

---

## Цель

Lab stack healthy (2 VM dev: server + web):
- `http://127.0.0.1:18080/api/v1/health` → 200
- `http://127.0.0.1:19088/` → 200

**Фасад:** `.\scripts\Start-KorusServerStack.ps1 -Mode warm` (полная матрица: `-Help`).  
**2 агента:** `.\scripts\Start-KorusServerStack.ps1 -EmitPrompt -Agent server|web`

## Диагностика (быстро)

```powershell
# WHPX обязателен (TCG запрещён)
.\scripts\qemu-fast-up.ps1 -ProbeOnly

# Host probes
curl -s -o NUL -w "%{http_code}" http://127.0.0.1:18080/api/v1/health
curl -s -o NUL -w "%{http_code}" http://127.0.0.1:19088/

# Guest job status (не блокировать IDE на 90 мин)
.\scripts\qemu-guest-job.ps1
```

Логи: `deploy/qemu/run/*-serial.log`, `deploy/qemu/run/status-minute.log`

## Цикл

1. **State** — QEMU VMs up? (`qemu-watch` / serial logs)
2. **If down** — `.\scripts\Start-KorusServerStack.ps1 -Mode warm` (или `-Mode fast`)
3. **If API unhealthy**
   - Launch: `.\scripts\qemu-sync-api-core.ps1 -NoCache`
   - Poll каждые 3 мин: `.\scripts\qemu-guest-job.ps1 -Loop`
   - Не убивать job из-за «зависшего» терминала IDE
4. **If web unhealthy** — `.\scripts\qemu-web-sync.ps1` или guest web redeploy
5. **If integrations** — `.\scripts\vpp\Wait-IntegrationsOnline.ps1`
6. **Verify** — health URLs 200
7. **Repeat** до обоих 200

## Полный redeploy (тяжёлый)

```powershell
.\scripts\qemu-redeploy.ps1 -Rebuild -Force
# Poll korus-redeploy.done каждые 3 мин, не только host health
```

## Запрещено

- Host Docker / `docker compose` / `full-stack-up.ps1`
- `KORUS_QEMU_FORCE_TCG` / TCG fallback (см. `qemu-whpx-required.mdc`)
- Блокирующий `qemu-sync-api-core -Wait` в IDE без явной просьбы
- Перезапуск guest job без анализа `/tmp/korus-job-*.log`

## Успех

Оба endpoint 200, guest job idle или completed OK.

Следующий шаг: вернуться к целевому циклу (VPP, UI tier, smoke).
