# 24h lab soak wrapper (spec 025 T904 / VP-21).
# Run inside korus-server QEMU guest or Linux host with stack up — NOT on Windows dev host.
param(
    [int]$Hours = 24,
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$WebBase = "http://127.0.0.1:9088",
    [int]$Connections = 50,
    [string]$OutDir = "deploy/qemu/run/soak"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$durationSec = $Hours * 3600
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$logDir = Join-Path $repoRoot $OutDir
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "soak-${Hours}h-$ts.log"

Write-Host "Soak: ${Hours}h, connections=$Connections, base=$BaseUrl"
Write-Host "Log: $logFile"

$env:BASE_URL = $BaseUrl
$env:WEB_BASE = $WebBase
$env:CONNECTIONS = "$Connections"
$env:DURATION_SEC = "$durationSec"

if (Test-Path "$repoRoot/scripts/load-ws-soak.ps1") {
    & "$repoRoot/scripts/load-ws-soak.ps1" *>&1 | Tee-Object -FilePath $logFile
} else {
    bash "$repoRoot/scripts/load-ws-soak.sh" 2>&1 | Tee-Object -FilePath $logFile
}

Write-Host "Soak finished. Capture heap baseline:"
Write-Host "  scripts/perf/README.md — post snapshot after soak"
