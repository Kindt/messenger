param(
    [string]$ApiBase = "http://127.0.0.1:18080/api",
    [string]$IntegrationsGateway = "http://127.0.0.1:18190",
    [switch]$SkipPreflight
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Test-PortUp {
    param([string]$Name, [string]$Url)
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
        Write-Host "[OK] $Name" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "[SKIP] $Name not reachable: $Url" -ForegroundColor Yellow
        return $false
    }
}

if (-not $SkipPreflight) {
    Write-Host "=== Integrations gate preflight ===" -ForegroundColor Cyan
    $apiOk = Test-PortUp "API health" "$ApiBase/v1/health"
    $gwOk = Test-PortUp "Integrations gateway" "$IntegrationsGateway/health"
    if (-not $apiOk -or -not $gwOk) {
        Write-Host "Preflight failed. Start: .\scripts\qemu-up.ps1 -WithIntegrations" -ForegroundColor Red
        exit 2
    }
}

function Test-TcpPortOpen {
    param([int]$Port)
    try {
        $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        return [bool]$t.TcpTestSucceeded
    } catch { return $false }
}

$failed = 0
$runs = @(
    @{ Run = { & (Join-Path $scriptDir "smoke-plugin-echo-php.ps1") -BaseUrl "http://127.0.0.1:18088" } },
    @{ Run = { & (Join-Path $scriptDir "smoke-plugin-exchange.ps1") -BaseUrl "http://127.0.0.1:18093" } },
    @{ Run = { & (Join-Path $scriptDir "smoke-plugin-storage.ps1") -BaseUrl "http://127.0.0.1:18094" } },
    @{ Run = { & (Join-Path $scriptDir "smoke-plugin-ocr-mock.ps1") -BaseUrl "http://127.0.0.1:18095" } },
    @{ Run = { & (Join-Path $scriptDir "smoke-plugin-ai-triage.ps1") -BaseUrl "http://127.0.0.1:18096" } },
    @{
        Run = { & (Join-Path $scriptDir "smoke-plugin-1c.ps1") -BaseUrl "http://127.0.0.1:18097" }
        SkipIf = { -not (Test-TcpPortOpen -Port 18097) }
        SkipMsg = "host :18097 not forwarded (restart korus-integrations VM once)"
    }
)
$i = 0
foreach ($entry in $runs) {
    $i++
    Write-Host "--- smoke $i/$($runs.Count) ---" -ForegroundColor DarkGray
    if ($entry.SkipIf -and (& $entry.SkipIf)) {
        Write-Host "[SKIP] $($entry.SkipMsg)" -ForegroundColor Yellow
        continue
    }
    try {
        & $entry.Run
    } catch {
        Write-Host "[FAIL] $_" -ForegroundColor Red
        $failed++
    }
}

if ($failed -gt 0) {
    Write-Host "Integrations gate: $failed failed" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Integrations gate passed (mock/auto backends)" -ForegroundColor Green
