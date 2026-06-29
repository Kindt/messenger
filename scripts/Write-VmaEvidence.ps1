#Requires -Version 5.1
# Write or patch VMA evidence manifest (spec 029). Does NOT close LSO rows.
param(
    [ValidateSet('L0', 'L1', 'L2', 'L3', 'L4')]
    [string]$Level = 'L2',
    [hashtable]$Gates = @{},
    [string[]]$Artifacts = @(),
    [string[]]$ScaffoldRuns = @(),
    [string[]]$AddonsEnabled = @(),
    [string]$OutPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\Write-VmaEvidence.ps1 -Level L2 -Gates @{ W1_regression = 'PASS' }

Writes deploy/qemu/run/vma-evidence/vma-evidence-YYYY-MM-DD-HHmmss.json
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$EvDir = Join-Path $Root "deploy\qemu\run\vma-evidence"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

if (-not $OutPath) {
    $OutPath = Join-Path $EvDir ("vma-evidence-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")
}

$defaultGates = @{
    buildIntegrity = "NOT_RUN"
    W1_regression = "NOT_RUN"
    W2_integrations = "NOT_RUN"
    W3_media = "NOT_RUN"
    W4_security = "NOT_RUN"
    W5_load = "NOT_RUN"
    W6_export_chain = "NOT_RUN"
    W7_fleet = "NOT_RUN"
    W8_deploy = "NOT_RUN"
    W_PLW_playwright = "NOT_RUN"
    W_ADDON_profiles = "NOT_RUN"
    W_SPEC_runners = "NOT_RUN"
}
foreach ($k in $Gates.Keys) { $defaultGates[$k] = $Gates[$k] }

$gitCommit = ""
$gitBranch = ""
try {
    $gitCommit = (git -C $Root rev-parse --short HEAD 2>$null)
    $gitBranch = (git -C $Root rev-parse --abbrev-ref HEAD 2>$null)
} catch { }

$scaffold = @()
foreach ($s in $ScaffoldRuns) {
    $scaffold += @{ script = $s; status = "SCaffold" }
}

$doc = @{
    spec = "029-qemu-vm-acceptance"
    level = $Level
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    git = @{ commit = $gitCommit; branch = $gitBranch }
    host = @{
        os = "windows"
        api_forward = "http://127.0.0.1:18080"
        web_forward = "http://127.0.0.1:19088"
        admin_forward = "http://127.0.0.1:18080/admin/"
    }
    qemu = @{ server = "unknown"; web = "unknown"; integrations = "not_required" }
    gates = $defaultGates
    scaffold_runs = $scaffold
    artifacts = @($Artifacts)
    addons_enabled = @($AddonsEnabled)
    ls_note = "LSO rows in spec 015 are NOT closed by this manifest"
    vma_rows_closed = @()
}

$doc | ConvertTo-Json -Depth 8 | Set-Content -Path $OutPath -Encoding utf8
Write-Host "[OK] VMA evidence -> $OutPath" -ForegroundColor Green
Write-Output $OutPath
