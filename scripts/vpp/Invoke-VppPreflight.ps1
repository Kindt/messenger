#Requires -Version 5.1
# VPP preflight — mandatory prerequisites for full run (spec 030). Fails fast, no SKIP.
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [switch]$RequireIntegrations,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppPreflight.ps1 [-RequireIntegrations]"
    exit 0
}

function Test-Tcp([int]$Port) {
    try {
        $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        return [bool]$t.TcpTestSucceeded
    } catch { return $false }
}

function Test-HttpOk([string]$Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400)
    } catch { return $false }
}

$errors = @()

if (-not (Test-HttpOk "$ApiBaseUrl/api/v1/health")) {
    $errors += "API unhealthy at $ApiBaseUrl - run .\scripts\qemu-up.ps1"
}
if (-not (Test-HttpOk "$WebBaseUrl/")) {
    $errors += "Web UI unreachable at $WebBaseUrl - run .\scripts\qemu-up.ps1 (web VM)"
}
if (-not (Test-Tcp 12221)) {
    $errors += "server VM SSH :12221 not open"
}
if (-not (Test-Tcp 12222)) {
    $errors += "web VM SSH :12222 not open"
}

if ($RequireIntegrations) {
    if (-not (Test-Tcp 12223)) {
        $errors += "integrations VM SSH :12223 not open - run .\scripts\qemu-up.ps1 -WithIntegrations"
    }
    foreach ($p in @(18088, 18093, 18094, 18095, 18096, 18097)) {
        if (-not (Test-Tcp $p)) {
            $errors += "plugin bridge port :$p not open (integrations VM / forwards)"
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "[FAIL] VPP preflight" -ForegroundColor Red
    foreach ($e in $errors) { Write-Host "  - $e" -ForegroundColor Yellow }
    exit 1
}

Write-Host "[OK] VPP preflight (API, Web, SSH$(if ($RequireIntegrations) { ', all plugin ports' }))" -ForegroundColor Green
