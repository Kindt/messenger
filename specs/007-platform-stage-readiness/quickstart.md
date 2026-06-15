# Quickstart: Spec 007 verification

## Host (always)

```powershell
.\gradlew.bat buildIntegrity --no-daemon
```

## QEMU inner loop

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode status
.\scripts\test-korus-wsurl.ps1
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
```

## QEMU outer gate (operator)

```powershell
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp
```

## Guest smokes (SSH to korus-server)

```bash
cd /mnt/korus
bash scripts/guest-smoke-platform-w2.sh
# optional export gate:
KORUS_RUN_EXPORT_PURGE_SMOKE=1 KORUS_API_URL=http://127.0.0.1:8080 bash scripts/guest-smoke-platform-w2.sh
```

## Load skeleton (host → forwarded API)

```powershell
$env:K6_BASE_URL = 'http://127.0.0.1:18080'
k6 run scripts/load/pilot-health.js --out json=deploy/qemu/run/k6-pilot-baseline.json
```

## Stage (when host available)

Follow [`deploy/ansible/inventory/stage/README.md`](../../deploy/ansible/inventory/stage/README.md).
