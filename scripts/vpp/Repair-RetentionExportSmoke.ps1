# Recreate retention-worker on QEMU server guest with fast scan for export smokes.
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host 'Repair: retention-worker fast scan on server guest...' -ForegroundColor Cyan
& (Join-Path $Root 'scripts\qemu-enable-regression-addons.ps1')
if ($LASTEXITCODE -ne 0) { throw 'qemu-enable-regression-addons failed' }

& (Join-Path $Root 'deploy\qemu\run\wait-api-health.ps1') -MaxMinutes 8
if ($LASTEXITCODE -ne 0) { throw 'API not ready after retention repair' }

Write-Host '[OK] retention-worker recreated with RETENTION_SCAN_INTERVAL_SECONDS=30' -ForegroundColor Green
