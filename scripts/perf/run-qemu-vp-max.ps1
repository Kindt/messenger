# Spec 025 maximum QEMU lab closure (no staging): VP-B/C subset + metrics + PG EXPLAIN + observability.
param(
    [string]$ApiBase = "http://127.0.0.1:18080",
    [string]$WebBase = "http://127.0.0.1:19088",
    [string]$WsBase = "ws://127.0.0.1:18082/ws",
    [switch]$SkipBuildIntegrity,
    [switch]$SkipMessagingE2e,
    [switch]$SkipWsSoak,
    [switch]$WriteEvidence,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($Help) {
    Write-Host @"
Usage: .\scripts\perf\run-qemu-vp-max.ps1 [-WriteEvidence]
  QEMU forwards :18080 / :19088 / :18082; SSH server :12221 for guest probes.
  Skips buildIntegrity by default (use full gate for PR).
"@
    exit 0
}

Set-Location $Root
$evidenceDir = Join-Path $PSScriptRoot "evidence"
$results = [ordered]@{ started_at = (Get-Date).ToUniversalTime().ToString("o"); steps = @() }

function Step($name, [scriptblock]$action) {
    Write-Host "=== $name ===" -ForegroundColor Cyan
    try {
        & $action
        $code = if ($null -ne $LASTEXITCODE) { $LASTEXITCODE } else { 0 }
        if ($code -ne 0) { throw "exit $code" }
        $results.steps += @{ name = $name; status = "PASS" }
    } catch {
        $results.steps += @{ name = $name; status = "FAIL"; error = "$_" }
        Write-Host "[FAIL] $name : $_" -ForegroundColor Red
        if ($WriteEvidence) { Save-Evidence "FAIL" }
        exit 1
    }
}

function Test-UsableBash {
    $bash = Get-Command bash -ErrorAction SilentlyContinue
    if (-not $bash) { return $false }
    # Windows ships a WSL installer stub at System32\bash.exe вЂ” not a real shell.
    if ($bash.Source -match '(?i)\\Windows\\System32\\bash\.exe$') { return $false }
    try {
        & bash -c "exit 0" 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Save-Evidence([string]$overall) {
    New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
    $sha = try { git rev-parse --short HEAD } catch { "unknown" }
    $payload = @{
        overall = $overall
        git_sha = $sha
        environment = "qemu-lab"
        api_base = $ApiBase
        finished_at = (Get-Date).ToUniversalTime().ToString("o")
        steps = $results.steps
    }
    $path = Join-Path $evidenceDir "$(Get-Date -Format yyyy-MM-dd)_qemu-vp-max.json"
    $payload | ConvertTo-Json -Depth 6 | Set-Content $path -Encoding UTF8
    Write-Host "Evidence: $path" -ForegroundColor Green
}

Step "VP-00 static" { & "$Root\scripts\perf\run-vp00-static.ps1" }

Step "API health" {
    $code = curl.exe -sS -m 15 -o NUL -w "%{http_code}" "$ApiBase/api/v1/health"
    if ($code -notmatch '^2') { throw "health $code" }
    Write-Host "health=$code"
}

if (-not $SkipBuildIntegrity) {
    Step "buildIntegrity" { & "$Root\gradlew.bat" buildIntegrity --no-daemon -q }
}

Step "smoke-korus-web" { & "$Root\scripts\smoke-korus-web.ps1" -WebBaseUrl $WebBase -CheckApi }

if (-not $SkipMessagingE2e) {
    Step "VP-13 messaging E2E" {
        if (Test-UsableBash) {
            & "$Root\scripts\smoke-messaging-e2e.ps1" -BaseUrl $ApiBase -WsUrl $WsBase -SkipEnsureUsers
        } else {
            . "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
            Invoke-QemuServerGuest -Script @'
set -euo pipefail
cd /mnt/korus
bash scripts/smoke-messaging-e2e.sh --url http://127.0.0.1:8080 --ws-url ws://127.0.0.1:8082/ws --skip-ensure-users
'@
        }
    }
}

Step "VP-14 file upload parity" {
    & "$Root\scripts\smoke-web-parity-api.ps1" -BaseUrl $ApiBase -SkipExport
}

Step "first-load budget" {
    Push-Location "$Root\modules\web-client\webui-build"
    npm run test:first-load --silent
    Pop-Location
}

Step "k6 pilot-health" {
    $k6 = Get-Command k6 -ErrorAction SilentlyContinue
    $outJson = "scripts/perf/evidence/k6-pilot-health.json"
    if ($k6) {
        $env:K6_BASE_URL = $ApiBase
        & k6 run --vus 5 --duration 30s --out "json=$outJson" "$Root\scripts\load\pilot-health.js"
    } else {
        & "$Root\scripts\perf\run-k6-docker.ps1" -Script "scripts/load/pilot-health.js" -BaseUrl $ApiBase -OutJson $outJson
    }
}

Step "k6 pilot-rest" {
    $restEnv = @{
        K6_USER = "smoke_user_a"
        K6_PASS = "smokepass123"
        K6_VUS = "2"
        K6_LAB = "1"
    }
    $k6 = Get-Command k6 -ErrorAction SilentlyContinue
    if ($k6) {
        $env:K6_BASE_URL = $ApiBase
        foreach ($k in $restEnv.Keys) { Set-Item -Path "env:$k" -Value $restEnv[$k] }
        & k6 run --no-thresholds "$Root\scripts\load\pilot-rest.js"
    } else {
        & "$Root\scripts\perf\run-k6-docker.ps1" -Script "scripts/load/pilot-rest.js" -BaseUrl $ApiBase `
            -NoThresholds -Env $restEnv
    }
}

Step "QEMU metrics probe" { & "$PSScriptRoot\run-qemu-metrics-probe.ps1" }

Step "QEMU PG EXPLAIN" { & "$PSScriptRoot\run-qemu-pg-explain.ps1" }

Step "QEMU observability lab" { & "$PSScriptRoot\run-qemu-observability-lab.ps1" }

if (-not $SkipWsSoak) {
    Step "VP-01 WS soak (short)" {
        . "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
        $out = Invoke-QemuServerGuest -Script @'
set -euo pipefail
cd /mnt/korus
CONNECTIONS=25 DURATION_SEC=90 \
BASE_URL=http://127.0.0.1:8080 \
WS_BASE=ws://127.0.0.1:8082/ws \
WS_ORIGIN=http://127.0.0.1:9088 \
METRICS_URL=http://127.0.0.1:9198/metrics \
SMOKE_USER=smoke_user_a SMOKE_USER_PASS=smokepass123 \
bash scripts/load-ws-soak.sh
'@
        Write-Host $out
        if ($out -match '\[FAIL\]') { throw "WS soak failed on guest" }
    }
}

Step "redis INFO (guest)" {
    . "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
    $out = Invoke-QemuServerGuest -Script @'
redis=$(docker ps --format '{{.Names}}' | grep redis | head -1)
if [ -z "$redis" ]; then echo "[FAIL] redis container missing"; exit 1; fi
info=$(docker exec "$redis" redis-cli INFO stats 2>/dev/null | grep -E 'keyspace_hits|keyspace_misses' || true)
echo "[OK] redis $redis $info"
'@
    Write-Host $out
    if ($out -match '\[FAIL\]') { throw "redis probe failed" }
}

if ($WriteEvidence) { Save-Evidence "PASS" }

Write-Host "[OK] QEMU VP max PASS" -ForegroundColor Green
