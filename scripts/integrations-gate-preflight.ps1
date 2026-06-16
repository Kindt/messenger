# Spec 014 — readiness check before integrations live gate (host + optional guest).
param(
    [switch]$Online,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\integrations-gate-preflight.ps1 [-Online]

Offline: Gradle modules, scripts, fixtures, .env.example.
-Online: probe QEMU host forwards (:18080, :18190, :18088-:18097) without running smokes.

Before live backends on real stand:
  1. Copy integrations/.env.example -> integrations/.env on guest
  2. Set INTEGRATIONS_BACKEND_MODE=live + credentials
  3. docker compose -f integrations/docker-compose.integrations.yml up -d --build
  4. .\scripts\smoke-integrations-gate.ps1
  5. Playwright with KORUS_INTEGRATIONS_GATE_URL
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$failed = 0

function Assert-File([string]$Path, [string]$Label) {
    if (Test-Path (Join-Path $root $Path)) {
        Write-Host "[OK] $Label" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] missing $Path" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "=== Integrations gate preflight (offline) ===" -ForegroundColor Cyan
$required = @(
    "integrations/.env.example",
    "integrations/docker-compose.integrations.yml",
    "integrations/demos/_lib/integration_backend.py",
    "integrations/_mock-servers/fixtures/ai/v1/triage.json",
    "integrations/_mock-servers/fixtures/1c/odata/Catalog_Items.json",
    "scripts/smoke-integrations-gate.ps1",
    "scripts/smoke-plugin-1c.ps1",
    "scripts/qemu-integrations-up.ps1",
    "tests/e2e-web/specs/plugin-integrations.spec.ts",
    "specs/014-bot-plugin-platform/contracts/integrations-live-gate.md",
    "modules/core-api/src/main/resources/db/migration/V038__plugin_1c_bridge.sql"
)
foreach ($f in $required) { Assert-File $f $f }

Write-Host "--- Gradle compile (common + plugins) ---" -ForegroundColor DarkGray
Push-Location $root
try {
    & .\gradlew :modules:common:compileJava :modules:core-api:compileJava `
        :modules:workers:onec-bridge:compileJava :modules:workers:exchange-bridge:compileJava `
        :modules:workers:storage-bridge:compileJava --no-daemon -q
    if ($LASTEXITCODE -ne 0) { $failed++; Write-Host "[FAIL] Gradle compile" -ForegroundColor Red }
    else { Write-Host "[OK] Gradle compile" -ForegroundColor Green }
} finally {
    Pop-Location
}

if ($Online) {
    Write-Host "=== Online probes ===" -ForegroundColor Cyan
    $urls = @(
        @{ Name = "API"; Url = "http://127.0.0.1:18080/api/v1/health" },
        @{ Name = "Gateway"; Url = "http://127.0.0.1:18190/health" }
    )
    foreach ($u in $urls) {
        try {
            Invoke-WebRequest -Uri $u.Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            Write-Host "[OK] $($u.Name)" -ForegroundColor Green
        } catch {
            Write-Host "[SKIP] $($u.Name) down: $($u.Url)" -ForegroundColor Yellow
            $failed++
        }
    }
    $ports = 18088, 18091, 18093, 18094, 18095, 18096, 18097
    foreach ($p in $ports) {
        $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $p -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if ($t.TcpTestSucceeded) {
            Write-Host "[OK] TCP :$p" -ForegroundColor Green
        } else {
            Write-Host "[SKIP] TCP :$p closed" -ForegroundColor Yellow
            $failed++
        }
    }
}

if ($failed -gt 0) {
    Write-Host "Preflight: $failed issue(s)" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Integrations gate preflight ready" -ForegroundColor Green
