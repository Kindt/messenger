# Enable EXPORT_AUTO_QUEUE_ON_SUGGESTED on QEMU server guest (VPP export_auto_queue_nats).
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host 'Repair: export auto-queue on server guest (recreate core-api)...' -ForegroundColor Cyan
& (Join-Path $Root 'scripts\qemu-enable-regression-addons.ps1')
if ($LASTEXITCODE -ne 0) { throw 'qemu-enable-regression-addons failed' }

& (Join-Path $Root 'deploy\qemu\run\wait-api-health.ps1') -MaxMinutes 8
if ($LASTEXITCODE -ne 0) { throw 'API not ready after export auto-queue repair' }

& (Join-Path $Root 'scripts\smoke-export-auto-queue-nats.ps1') -BaseUrl 'http://127.0.0.1:18080'
if ($LASTEXITCODE -ne 0) { throw 'smoke-export-auto-queue-nats failed after repair' }

Write-Host '[OK] export auto-queue NATS smoke on :18080' -ForegroundColor Green
