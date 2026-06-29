#Requires -Version 5.1
# Write VPP evidence manifest (spec 030 — Всеобъемлющая проверка продукта).
param(
    [ValidateSet('quick', 'standard', 'full')]
    [string]$Level = 'standard',
    [hashtable]$Gates = @{},
    [hashtable]$Dimensions = @{},
    [string[]]$Artifacts = @(),
    [string[]]$AddonsEnabled = @(),
    [hashtable]$UxSummary = @{},
    [string]$OutPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\Write-VppEvidence.ps1 -Level standard -Gates @{ buildIntegrity = 'PASS' } -Dimensions @{ 'VPP-1' = @{ status = 'PASS' } }

Writes deploy/qemu/run/vpp-evidence/vpp-evidence-YYYY-MM-DD-HHmmss.json
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

if (-not $OutPath) {
    $OutPath = Join-Path $EvDir ("vpp-evidence-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")
}

$defaultDims = @{
    "VPP-1" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-2" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-3" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-4" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-5" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-6" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-7" = @{ status = "NOT_RUN"; gates = @{} }
    "VPP-8" = @{ status = "NOT_RUN"; gates = @{} }
}
foreach ($k in $Dimensions.Keys) {
    $defaultDims[$k] = $Dimensions[$k]
}

$gitCommit = ""
$gitBranch = ""
try {
    $gitCommit = (git -C $Root rev-parse --short HEAD 2>$null)
    $gitBranch = (git -C $Root rev-parse --abbrev-ref HEAD 2>$null)
} catch { }

$doc = [ordered]@{
    spec = "030-vpp-product-verification"
    title = "Всеобъемлющая проверка продукта"
    level = $Level
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    git = @{ commit = $gitCommit; branch = $gitBranch }
    host = @{
        os = "windows"
        api_forward = "http://127.0.0.1:18080"
        web_forward = "http://127.0.0.1:19088"
        admin_forward = "http://127.0.0.1:18080/admin/"
    }
    dimensions = $defaultDims
    gates = $Gates
    ux_summary = $UxSummary
    addons_enabled = @($AddonsEnabled)
    artifacts = @($Artifacts)
    ls_note = "LSO rows in spec 015 are NOT closed by VPP evidence"
    vma_cross_ref = "specs/029-qemu-vm-acceptance"
}

$doc | ConvertTo-Json -Depth 10 | Set-Content -Path $OutPath -Encoding utf8
Write-Host "[OK] VPP evidence -> $OutPath" -ForegroundColor Green
Write-Output $OutPath
