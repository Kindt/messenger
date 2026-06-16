# Spec 014 — all plugin sidecar smokes via QEMU host forwards (integrations VM).
param(
    [int]$WaitSec = 1200,
    [switch]$SkipWait,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-plugin-qemu.ps1 [-WaitSec 1200] [-SkipWait]

Waits for integrations guest services, then runs:
  smoke-plugin-echo-php, exchange, storage, ocr, ai-triage
Optional: connector :18091 health GET

Requires: .\scripts\qemu-integrations-up.ps1 (or qemu-up -WithIntegrations)
"@
    exit 0
}

function Test-PluginPort {
    param([int]$Port)
    try {
        $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        return [bool]$t.TcpTestSucceeded
    } catch { return $false }
}

function Wait-IntegrationsReady {
    param([int]$MaxSec)
    $deadline = (Get-Date).AddSeconds($MaxSec)
    $ports = @(18190, 18088, 18093, 18094, 18095, 18096, 18097)
    while ((Get-Date) -lt $deadline) {
        $ok = $true
        foreach ($p in $ports) {
            if (-not (Test-PluginPort $p)) { $ok = $false; break }
        }
        if ($ok) {
            try {
                $h = Invoke-WebRequest -Uri "http://127.0.0.1:18190/health" -UseBasicParsing -TimeoutSec 5
                if ($h.StatusCode -eq 200) { return $true }
            } catch {}
        }
        Write-Host "  waiting integrations stack..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 15
    }
    return $false
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not $SkipWait) {
        if (-not (Test-PluginPort 12223)) {
            Write-Host "Integrations VM SSH :12223 down - run .\scripts\qemu-integrations-up.ps1" -ForegroundColor Yellow
            exit 2
        }
        Write-Host "=== wait integrations stack (max ${WaitSec}s) ===" -ForegroundColor Cyan
        if (-not (Wait-IntegrationsReady -MaxSec $WaitSec)) {
            Write-Host "[FAIL] integrations stack not ready" -ForegroundColor Red
            exit 1
        }
        Write-Host "[OK] integrations ports open" -ForegroundColor Green
    }

    $smokes = @(
        @{ Name = "echo-php"; Script = "smoke-plugin-echo-php.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18088" } },
        @{ Name = "exchange"; Script = "smoke-plugin-exchange.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18093" } },
        @{ Name = "storage"; Script = "smoke-plugin-storage.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18094" } },
        @{ Name = "ocr"; Script = "smoke-plugin-ocr-mock.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18095" } },
        @{ Name = "ai-triage"; Script = "smoke-plugin-ai-triage.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18096" } },
        @{ Name = "1c"; Script = "smoke-plugin-1c.ps1"; Args = @{ BaseUrl = "http://127.0.0.1:18097" } }
    )

    foreach ($s in $smokes) {
        Write-Host "--- $($s.Name) ---" -ForegroundColor Cyan
        & (Join-Path $PSScriptRoot $s.Script) -BaseUrl $s.Args.BaseUrl
    }

    try {
        $body = '{"event_id":"smoke-conn","type":"mention","text":"ping"}'
        $null = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18091/v1/plugin/handle" -ContentType "application/json" -Body $body -TimeoutSec 10
        Write-Host "[OK] connector-runtime handle :18091" -ForegroundColor Green
    } catch {
        Write-Warning "connector-runtime :18091 skip: $_"
    }

    Write-Host "[OK] smoke-plugin-qemu all green" -ForegroundColor Green
} finally {
    Pop-Location
}
