# Spec 025 lab gate: VP-00 static + QEMU health + web smoke + optional k6 (no staging FQDN).
param(
    [string]$ApiBase = "http://127.0.0.1:18080",
    [string]$WebBase = "http://127.0.0.1:19088",
    [switch]$SkipK6,
    [switch]$WriteBaseline,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($Help) {
    Write-Host @"
Usage: .\scripts\perf\run-qemu-lab-gate.ps1 [-WriteBaseline] [-SkipK6]
  Requires QEMU port-forwards :18080 / :19088.
"@
    exit 0
}

Set-Location $Root
Write-Host "=== VP-00 static ===" -ForegroundColor Cyan
& "$Root\scripts\perf\run-vp00-static.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== buildIntegrity (host) ===" -ForegroundColor Cyan
& "$Root\gradlew.bat" buildIntegrity --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== QEMU health ===" -ForegroundColor Cyan
$apiCode = curl.exe -sS -m 15 -o NUL -w "%{http_code}" "$ApiBase/api/v1/health" 2>$null
if (-not $apiCode) { $apiCode = "down" }
$webCode = curl.exe -sS -m 15 -o NUL -w "%{http_code}" "$WebBase/" 2>$null
if (-not $webCode) { $webCode = "down" }
Write-Host "API $ApiBase/api/v1/health -> $apiCode"
Write-Host "Web $WebBase/ -> $webCode"
if ($apiCode -notmatch '^2') {
    Write-Host "[FAIL] API not healthy on QEMU forwards" -ForegroundColor Red
    exit 2
}

Write-Host "=== smoke-korus-web ===" -ForegroundColor Cyan
& "$Root\scripts\smoke-korus-web.ps1" -WebBaseUrl $WebBase -CheckApi
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== first-load budget ===" -ForegroundColor Cyan
Push-Location "$Root\modules\web-client\webui-build"
npm run test:first-load --silent
$fl = $LASTEXITCODE
Pop-Location
if ($fl -ne 0) { exit $fl }

if (-not $SkipK6) {
    $k6 = Get-Command k6 -ErrorAction SilentlyContinue
    if ($k6) {
        Write-Host "=== k6 pilot-health (30s) ===" -ForegroundColor Cyan
        $env:K6_BASE_URL = $ApiBase
        k6 run --vus 3 --duration 30s scripts/load/pilot-health.js
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } else {
        Write-Host "[skip] k6 not installed on host" -ForegroundColor Yellow
    }
}

if ($WriteBaseline) {
    $sha = git rev-parse --short HEAD
    $dir = "$Root\scripts\perf\baselines"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $bundleBytes = (curl.exe -sS -m 20 -o NUL -w "%{size_download}" "$WebBase/app.bundle.js" 2>$null)
    $healthMs = [math]::Round([double](curl.exe -sS -m 20 -o NUL -w "%{time_total}" "$ApiBase/api/v1/health" 2>$null) * 1000, 1)
    $out = @{
        wave = "E"
        phase = "post-qemu"
        git_sha = $sha
        environment = "qemu-lab"
        metrics = @{
            web_first_load_kb = if ($bundleBytes) { [math]::Round([double]$bundleBytes / 1024, 1) } else { $null }
            api_health_ms = $healthMs
            web_ui_http = $webCode
            api_health_http = $apiCode
        }
        notes = "Captured by scripts/perf/run-qemu-lab-gate.ps1"
    }
    $path = Join-Path $dir "$(Get-Date -Format yyyy-MM-dd)_wave-E_qemu-lab.json"
    $out | ConvertTo-Json -Depth 5 | Set-Content $path -Encoding UTF8
    Write-Host "Baseline written: $path" -ForegroundColor Green
}

Write-Host "[OK] QEMU lab gate PASS" -ForegroundColor Green
