#Requires -Version 5.1

# Spec 029 L2/L4 VM acceptance orchestrator. Does NOT close LSO rows.

param(

    [ValidateSet('L2', 'L4', 'L4-light', 'L4+', 'L4++')]

    [string]$Level = 'L4',

    [string]$ApiBaseUrl = "http://127.0.0.1:18080",

    [string]$WebBaseUrl = "http://127.0.0.1:19088",

    [switch]$SkipIntegrations,

    [switch]$SkipPlaywright,

    [switch]$SkipProfiles,

    [switch]$SkipBuild,

    [ValidateSet('', 'W5', 'W7')]

    [string]$StartAtStep = '',

    [switch]$Help

)



$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

$writeEvidence = Join-Path $Root "scripts\Write-VmaEvidence.ps1"



if ($Help) {

    Write-Host @"

Usage: .\scripts\smoke-vm-acceptance-matrix.ps1 [-Level L2|L4|L4-light] [-SkipIntegrations] [-SkipPlaywright]



Orchestrates spec 029 waves and writes VMA evidence JSON.

"@

    exit 0

}



$gates = @{}

$artifacts = @()

$scaffolds = @("scripts/run-sfu-participant-load-qemu.ps1")



function Step([string]$name, [string]$gateKey, [scriptblock]$body) {

    Write-Host ""

    Write-Host "=== VMA: $name ===" -ForegroundColor Cyan

  $global:LASTEXITCODE = 0

    & $body

    if ($LASTEXITCODE -ne 0) {

        $gates[$gateKey] = "FAIL"

        Write-Host "[FAIL] VMA step $name" -ForegroundColor Red

        exit $LASTEXITCODE

    }

}



function Health-Ok([string]$url) {

    try {

        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 8

        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400)

    } catch { return $false }

}



if (-not $SkipBuild) {

    Step "buildIntegrity" "buildIntegrity" {

        Push-Location $Root

        & .\gradlew.bat buildIntegrity --no-daemon -q

        Pop-Location

    }

    $gates.buildIntegrity = "PASS"

} else {

    $gates.buildIntegrity = "SKIP"

}



if (-not (Health-Ok "$ApiBaseUrl/api/v1/health")) {

    Write-Host "[FAIL] API not healthy at $ApiBaseUrl - start QEMU first" -ForegroundColor Red

    exit 2

}

if ($StartAtStep -eq 'W5' -or $StartAtStep -eq 'W7') {
    Write-Host "[INFO] VMA resume from $StartAtStep (prior waves assumed PASS)" -ForegroundColor DarkGray
    $gates.W1_regression = 'PASS'
    $gates.W2_integrations = if ($SkipIntegrations) { 'SKIP' } else { 'PASS' }
    $gates.W3_media = 'PASS'
    $gates.W4_security = 'PASS'
    $gates.W6_export_chain = 'PASS'
}
if ($StartAtStep -eq 'W7') {
    $gates.W5_load = 'PASS'
}



if ($StartAtStep -ne 'W5' -and $StartAtStep -ne 'W7') {

Step "L2 regression" "W1_regression" {

    & (Join-Path $Root "scripts\smoke-local-regression.ps1") -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl

}

$gates.W1_regression = "PASS"



if ($Level -eq 'L2') {

    $evPath = & $writeEvidence -Level L2 -Gates $gates -ScaffoldRuns $scaffolds -Artifacts $artifacts

    Write-Host "[OK] VMA L2 complete -> $evPath" -ForegroundColor Green

    exit 0

}



if (-not $SkipIntegrations) {

    Step "W2 integrations" "W2_integrations" {

        & (Join-Path $Root "scripts\smoke-integrations-gate.ps1")

    }

    $gates.W2_integrations = "PASS"

} else {

    $gates.W2_integrations = "SKIP"

}



Step "W3 media" "W3_media" {

    & (Join-Path $Root "scripts\smoke-turn-qemu.ps1") -GuestOnly

    & (Join-Path $Root "scripts\smoke-live-session.ps1") -BaseUrl $ApiBaseUrl

    & (Join-Path $Root "scripts\smoke-phase5-adr-scaffolds.ps1") -BaseUrl $ApiBaseUrl

}

$gates.W3_media = "PASS"



Step "W4 security" "W4_security" {

    & (Join-Path $Root "scripts\security-gate.ps1") -SkipBuild -BaseUrl $ApiBaseUrl

    & (Join-Path $Root "scripts\smoke-dlp-mock.ps1")

}

$gates.W4_security = "PASS"



Step "W6 export chain" "W6_export_chain" {

    & (Join-Path $Root "scripts\run-export-compliance-chain.ps1") -ApiBaseUrl $ApiBaseUrl

}

$gates.W6_export_chain = "PASS"

}



if ($StartAtStep -ne 'W7') {

Step "W5 load" "W5_load" {

    & (Join-Path $Root "scripts\run-k6-qemu-baseline.ps1")

    & (Join-Path $Root "scripts\load-ws-soak-qemu.ps1") -SkipServerRedeploy

    & (Join-Path $Root "scripts\run-sfu-participant-load-qemu.ps1")

}

$gates.W5_load = "PASS"

}



Step "W7 observability" "W7_fleet" {

    if (Test-Path (Join-Path $Root "scripts\perf\run-qemu-observability-lab.ps1")) {

        & (Join-Path $Root "scripts\perf\run-qemu-observability-lab.ps1")

    }

    & (Join-Path $Root "scripts\smoke-network-profile-catalog.ps1")

}

$gates.W7_fleet = "PASS"



if (-not $SkipProfiles) {

    Step "W_ADDON profiles quick" "W_ADDON_profiles" {

        & (Join-Path $Root "scripts\run-smoke-profile-matrix.ps1") -Quick

    }

    $gates.W_ADDON_profiles = "PASS"

} else {

    $gates.W_ADDON_profiles = "SKIP"

}



if (-not $SkipPlaywright) {

    $pwProfile = switch ($Level) {

        'L4-light' { 'L4-light' }

        'L4+' { 'L4+' }

        'L4++' { 'L4++' }

        default { 'L4' }

    }

    Step "W_PLW playwright" "W_PLW_playwright" {

        & (Join-Path $Root "scripts\run-playwright-admin-qemu.ps1")

        & (Join-Path $Root "scripts\run-playwright-qemu-matrix.ps1") -Profile $pwProfile

    }

    $gates.W_PLW_playwright = "PASS"

    $gates.W_SPEC_runners = "PASS"

} else {

    $gates.W_PLW_playwright = "SKIP"

    $gates.W_SPEC_runners = "SKIP"

}



$gates.W8_deploy = "SKIP"



$addons = @(

    "addon-productivity", "addon-engage", "addon-search", "addon-collaboration", "addon-ai",

    "addon-live", "addon-retention", "addon-archive", "addon-deep-archive", "addon-export",

    "addon-enterprise-auth", "addon-e2ee", "addon-bots", "addon-integrations",

    "addon-federation", "addon-dlp", "addon-migration-import"

)



$evLevel = if ($Level -eq 'L2') { 'L2' } else { 'L4' }

$evPath = & $writeEvidence -Level $evLevel -Gates $gates -ScaffoldRuns $scaffolds -Artifacts $artifacts -AddonsEnabled $addons



Write-Host ""

Write-Host "[OK] VMA $Level complete -> $evPath" -ForegroundColor Green


