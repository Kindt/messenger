#Requires -Version 5.1
# VPP-4: cross-module interaction chains from module-interaction-matrix.json (spec 030).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string[]]$OnlyChain = @(),
    [switch]$Quick,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-module-interactions.ps1 [-Quick] [-OnlyChain message-pipeline-core]

Runs cross-module smoke chains. -Quick runs core subset only.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$matrixPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\module-interaction-matrix.json"
if (-not (Test-Path $matrixPath)) { Write-Error "missing $matrixPath"; exit 1 }

$matrix = Get-Content -Raw -Path $matrixPath | ConvertFrom-Json
$quickIds = @(
    "message-pipeline-core",
    "message-dlp-gate",
    "productivity-phase5",
    "web-parity-full",
    "federation-trust-member"
)

$chains = @($matrix.chains)
if ($OnlyChain.Count -gt 0) {
    $chains = @($chains | Where-Object { $OnlyChain -contains $_.id })
} elseif ($Quick) {
    $chains = @($chains | Where-Object { $quickIds -contains $_.id })
}

if ($chains.Count -eq 0) {
    Write-Error "no chains to run"
    exit 1
}

function Ensure-SmokeAccessToken {
    if ($env:SMOKE_ACCESS_TOKEN) { return }
    $login = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $env:SMOKE_ACCESS_TOKEN = if ($login.access_token) { $login.access_token } else { $login.accessToken }
    if (-not $env:SMOKE_ACCESS_TOKEN) { throw "admin login returned no access token" }
}

Ensure-SmokeAccessToken

$failed = 0
$passed = 0
$skipped = 0

foreach ($chain in $chains) {
    $scriptRel = $chain.script
    $scriptPath = Join-Path $Root ($scriptRel -replace '/', '\')
    Write-Host ""
    Write-Host "=== chain: $($chain.id) ===" -ForegroundColor Cyan
    Write-Host "  $($chain.label)" -ForegroundColor DarkGray

    if (-not (Test-Path $scriptPath)) {
        Write-Host "[FAIL] missing $scriptRel" -ForegroundColor Red
        exit 1
    }

    $global:LASTEXITCODE = 0
    if ($scriptRel -like "*.sh") {
        $env:BASE_URL = $ApiBaseUrl
        & bash $scriptPath
    } elseif ($scriptRel -like "*.ps1") {
        if ($scriptRel -match "web-parity") {
            & $scriptPath -ApiBaseUrl $ApiBaseUrl
        } elseif ($scriptRel -match "export-compliance") {
            & $scriptPath -ApiBaseUrl $ApiBaseUrl
        } elseif ($scriptRel -match "ip-allowlist") {
            & $scriptPath -BaseUrl $ApiBaseUrl -RequireEnforce
        } elseif ($scriptRel -match "dlp-mock") {
            & $scriptPath -SkipIfUnreachable
        } elseif ($scriptRel -match "read-receipts") {
            & $scriptPath -BaseUrl "$ApiBaseUrl/api"
        } elseif ($scriptRel -match "voice-message|federation-trust|bot-api|phase5|migration|dlp|live-session|scim|openmls") {
            & $scriptPath -BaseUrl $ApiBaseUrl
        } else {
            & $scriptPath
        }
    } else {
        Write-Host "[FAIL] unknown script type $scriptRel" -ForegroundColor Red
        exit 1
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] chain $($chain.id) exit $LASTEXITCODE" -ForegroundColor Red
        $failed++
        if (-not $Quick) { exit $LASTEXITCODE }
    } else {
        Write-Host "[OK] chain $($chain.id)" -ForegroundColor Green
        $passed++
    }
}

Write-Host ""
if ($failed -gt 0) {
    Write-Host "[FAIL] module interactions: $passed pass, $failed fail, $skipped skip" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] module interactions ($passed chains, $skipped skipped)" -ForegroundColor Green
