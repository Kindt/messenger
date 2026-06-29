#Requires -Version 5.1
# Execute a single VPP comprehensive gate by id (spec 030).
param(
    [Parameter(Mandatory)][string]$GateId,
    [string]$ManifestPath = "",
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [int]$StartAfterTestIndex = 0,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $ManifestPath) {
    $ManifestPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json"
}
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppGateRunner.ps1 -GateId stack_health"
    exit 0
}

$manifest = Get-Content -Raw $ManifestPath | ConvertFrom-Json
$def = $manifest.gates.$GateId
if (-not $def) {
    Write-Host "[FAIL] unknown gate: $GateId" -ForegroundColor Red
    exit 1
}

$env:VPP_ZERO_SKIP = "1"
. (Join-Path $Root "deploy\qemu\lib\Get-KorusStackWaitParams.ps1")
$stackWait = Get-KorusStackWaitParams
$wsUrl = ($ApiBaseUrl -replace ':18080', ':18082') -replace '^http', 'ws'
if ($wsUrl -notmatch '/ws$') { $wsUrl = $wsUrl.TrimEnd('/') + '/ws' }

function Expand-Arg([string]$a) {
    $apiBase = $ApiBaseUrl.TrimEnd('/') + '/api'
    return $a.Replace('{ApiBaseUrl}', $ApiBaseUrl).Replace('{WebBaseUrl}', $WebBaseUrl).Replace('{WsUrl}', $wsUrl).Replace('{ApiBase}', $apiBase)
}

function ConvertTo-GateSplat {
    param([object[]]$GateArgs)
    $ht = @{}
    for ($i = 0; $i -lt $GateArgs.Count; $i++) {
        $a = [string]$GateArgs[$i]
        if ($a -match '^-(?<name>[A-Za-z0-9_]+)$') {
            $name = $Matches.name
            if ($i + 1 -lt $GateArgs.Count -and [string]$GateArgs[$i + 1] -notmatch '^-') {
                $ht[$name] = $GateArgs[$i + 1]
                $i++
            } else {
                $ht[$name] = $true
            }
        }
    }
    return $ht
}

Write-Host ""
Write-Host "=== VPP gate: $GateId ===" -ForegroundColor Cyan
if ($def.note) { Write-Host "    $($def.note)" -ForegroundColor DarkGray }

if ($GateId -eq 'integrations_gate') {
    Write-Host "  ensuring integrations VM + gateway..." -ForegroundColor DarkGray
    & (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') -MaxSec 900 -StartVmIfDown -RepairGateway
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

switch ($def.type) {
    "script" {
        $scriptPath = Join-Path $Root ($def.script -replace '/', '\')
        if (-not (Test-Path $scriptPath)) {
            Write-Host "[FAIL] missing script $scriptPath" -ForegroundColor Red
            exit 1
        }
        $gateArgs = @()
        if ($def.args) {
            foreach ($a in @($def.args)) { $gateArgs += (Expand-Arg $a) }
        }
        if ($def.script -like "*.sh") {
            $env:BASE_URL = $ApiBaseUrl
            $env:WS_URL = $wsUrl
            $env:WEB_BASE_URL = $WebBaseUrl
            & bash $scriptPath @gateArgs
        } else {
            $splat = ConvertTo-GateSplat -GateArgs $gateArgs
            & $scriptPath @splat
        }
        exit $LASTEXITCODE
    }
    "playwright-tier" {
        $tier = $def.tier
        if ($tier -eq 'ui-ux-full') { $env:UI_TESTS_PROFILE = 'full'; $env:UI_TESTS_UX_STRICT = '1'; $env:UI_CONSOLE_GUARD = '1' }
        if ($tier -eq 'ui-ux-pr') { $env:UI_TESTS_PROFILE = 'pr' }
        if ($tier -eq 'ui-ux-smoke') { $env:UI_TESTS_PROFILE = 'smoke' }
        & (Join-Path $Root "scripts\playwright-dev-loop.ps1") -Tier $tier -WaitForStack `
            -WaitTimeoutMinutes $stackWait.TimeoutMinutes -WaitIntervalSec $stackWait.IntervalSec `
            -WaitBusyIntervalSec $stackWait.BusyIntervalSec -MaxMaintenanceMinutes $stackWait.MaxMaintenanceMinutes `
            -StartAfterTestIndex $StartAfterTestIndex
        if ($tier -eq 'ui-ux-full' -and $LASTEXITCODE -eq 0) {
            & (Join-Path $Root "scripts\Assert-UiTestsUxFullGate.ps1")
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            & (Join-Path $Root "scripts\Assert-UiTestsConsoleGate.ps1")
        }
        exit $LASTEXITCODE
    }
    "playwright-matrix" {
        & (Join-Path $Root "scripts\run-playwright-qemu-matrix.ps1") -Profile $def.profile
        exit $LASTEXITCODE
    }
    "playwright-admin" {
        & (Join-Path $Root "scripts\run-playwright-admin-qemu.ps1")
        exit $LASTEXITCODE
    }
    "gradlew" {
        Push-Location $Root
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & .\gradlew.bat buildIntegrity --no-daemon -q 2>&1 | ForEach-Object {
                if ($_ -is [System.Management.Automation.ErrorRecord]) { Write-Host $_.ToString() }
                else { Write-Output $_ }
            } | Out-Host
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prevEap
            Pop-Location
        }
        exit $code
    }
    "catalog-audit" {
        & (Join-Path $Root "scripts\vpp\Invoke-VppCoverageAudit.ps1")
        exit $LASTEXITCODE
    }
    "preflight" {
        & (Join-Path $Root "scripts\vpp\Invoke-VppPreflight.ps1") -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl -RequireIntegrations
        exit $LASTEXITCODE
    }
    "coverage-report" {
        Write-Host "[INFO] coverage-report runs from orchestrator with -Gates hashtable" -ForegroundColor DarkGray
        exit 0
    }
    default {
        Write-Host "[FAIL] unknown gate type $($def.type)" -ForegroundColor Red
        exit 1
    }
}
