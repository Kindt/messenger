#Requires -Version 5.1
# Run smoke-profiles.json entries (spec 029 EP-2). Stack must match profile addons (full regression recommended).
param(
    [string]$ProfilesFile = "",
    [string[]]$OnlyProfile = @(),
    [switch]$Quick,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

function Resolve-SmokeScriptForLab {
    param([string]$ScriptRel)
    $map = @{
        'scripts/smoke-ready.sh' = 'scripts/smoke-ready.ps1'
        'scripts/smoke-hotplug-indexer.sh' = 'scripts/smoke-hotplug-indexer.ps1'
        'scripts/smoke-deploy-acceptance.sh' = 'scripts/smoke-deploy-acceptance-qemu.ps1'
    }
    if ($map.ContainsKey($ScriptRel)) { return $map[$ScriptRel] }
    return $ScriptRel
}
function Get-SmokeBashPath {
    $gitBash = Join-Path ${env:ProgramFiles} "Git\bin\bash.exe"
    if (Test-Path $gitBash) { return $gitBash }
    $cmd = Get-Command bash -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source -notmatch '\\Windows\\System32\\bash\.exe$') { return $cmd.Source }
    throw "bash not found (install Git for Windows; avoid WSL store stub)"
}

if (-not $ProfilesFile) {
    $ProfilesFile = Join-Path $Root "scripts\smoke-profiles.json"
}
if ($Help) {
    Write-Host @"
Usage: .\scripts\run-smoke-profile-matrix.ps1 [-OnlyProfile base+engage] [-Quick]

-Quick runs only reference-all profile smoke_scripts entry (deploy-acceptance).
"@
    exit 0
}

if (-not (Test-Path $ProfilesFile)) { Write-Error "missing $ProfilesFile"; exit 1 }
$json = Get-Content -Raw -Path $ProfilesFile | ConvertFrom-Json
$profiles = $json.profiles

$names = if ($OnlyProfile.Count -gt 0) { $OnlyProfile } elseif ($Quick) { @("reference-all") } else { @($profiles.PSObject.Properties.Name) }

$results = @()
foreach ($name in $names) {
    $p = $profiles.$name
    if (-not $p) {
        Write-Host "[SKIP] unknown profile $name"
        continue
    }
    Write-Host ""
    Write-Host "=== profile: $name ===" -ForegroundColor Cyan
    Write-Host "  $($p.description)"
    $profileOk = $true
    foreach ($scriptRel in $p.smoke_scripts) {
        $scriptRel = Resolve-SmokeScriptForLab -ScriptRel $scriptRel
        $scriptPath = Join-Path $Root ($scriptRel -replace '/', '\')
        if (-not (Test-Path $scriptPath)) {
            Write-Host "[FAIL] missing $scriptRel"
            $profileOk = $false
            break
        }
        Write-Host "  -> $scriptRel"
        if ($scriptRel -like "*.sh") {
            $env:BASE_URL = "http://127.0.0.1:18080"
            $env:KORUS_API_URL = "http://127.0.0.1:18080"
            & (Get-SmokeBashPath) $scriptPath
        } elseif ($scriptRel -like "*.ps1") {
            $env:BASE_URL = "http://127.0.0.1:18080"
            if ($scriptRel -like '*smoke-ready.ps1') {
                & $scriptPath -BaseUrl "http://127.0.0.1:18080"
            } elseif ($scriptRel -like '*deep-archive-chunks.ps1') {
                & $scriptPath -BaseUrl "http://127.0.0.1:18080"
            } elseif ($scriptRel -like '*retention-worker.ps1') {
                & $scriptPath -ApiBaseUrl "http://127.0.0.1:18080"
            } elseif ($scriptRel -like '*hotplug-indexer.ps1') {
                . (Join-Path $Root 'scripts\lib\Ensure-NatsQemuTunnel.ps1')
                $nats = Ensure-NatsQemuTunnel
                & $scriptPath -NatsUrl $nats
            } elseif ($scriptRel -like '*retention-purge.ps1') {
                & $scriptPath -BaseUrl "http://127.0.0.1:18080"
            } elseif ($scriptRel -like '*export-compliance*.ps1') {
                . (Join-Path $Root 'scripts\lib\Resolve-QemuLabWorkerMetrics.ps1')
                $m = Resolve-QemuLabWorkerMetrics -ApiBaseUrl 'http://127.0.0.1:18080'
                if ($scriptRel -like '*export-compliance-pack.ps1') {
                    & $scriptPath -BaseUrl 'http://127.0.0.1:18080' `
                        -WorkerMetricsUrl $m.WorkerMetricsUrl -RetentionMetricsUrl $m.RetentionMetricsUrl
                } else {
                    & $scriptPath -BaseUrl 'http://127.0.0.1:18080'
                }
            } elseif ($scriptRel -like '*deploy-acceptance-qemu.ps1') {
                & $scriptPath -ApiBaseUrl 'http://127.0.0.1:18080' -WebBaseUrl 'http://127.0.0.1:19088'
            } else {
                & $scriptPath
            }
        } else {
            Write-Host "[SKIP] unknown script type $scriptRel"
            continue
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] $scriptRel exit $LASTEXITCODE"
            $profileOk = $false
            break
        }
    }
    if ($p.playwright_tiers) {
        foreach ($tier in $p.playwright_tiers) {
            Write-Host "  -> playwright tier $tier (skipped in profile matrix; use run-playwright-qemu-matrix)"
        }
    }
    $status = if ($profileOk) { "PASS" } else { "FAIL" }
    $results += @{ profile = $name; status = $status }
    if (-not $profileOk) { Write-Host "[FAIL] profile $name" -ForegroundColor Red; exit 1 }
    Write-Host "[OK] profile $name" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] smoke profile matrix ($($names.Count) profiles)" -ForegroundColor Green
