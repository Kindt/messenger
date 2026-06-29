#Requires -Version 5.1
# Static fortress gap report — what is covered vs excluded by policy (spec 030).
param(
    [string]$CatalogPath = "",
    [string]$OutPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppFortressGapReport.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $CatalogPath) {
    $CatalogPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-coverage-catalog.json"
}
$catalog = Get-Content -Raw $CatalogPath | ConvertFrom-Json

$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
if (-not $OutPath) {
    $OutPath = Join-Path $EvDir ("vpp-fortress-gap-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")
}

$fortress = @($catalog.fortress_gates_ordered | ForEach-Object {
    $m = $catalog.fortress_gates.$_
    @{
        gate = $_
        script = $m.script
        closes_gap = $m.closes_gap
        dimension = $m.dimension
    }
})

$excluded = @($catalog.fortress_excluded_by_policy)

$doc = [ordered]@{
    spec = "030-vpp-product-verification"
    title = "VPP fortress gap analysis"
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    coverage_target_pct = $catalog.coverage_policy.fortress_target_pct
    tiers = @{
        base = @{ gates = @($catalog.full_gates_ordered | Where-Object { $_ -ne "coverage_report" }).Count; pct = 100 }
        extended = @{ gates = @($catalog.extended_gates_ordered).Count; pct = 110 }
        fortress = @{ gates = @($catalog.fortress_gates_ordered).Count; pct = 160 }
    }
    fortress_gates = $fortress
    excluded_by_policy = $excluded
    still_manual_outside_160 = @(
        @{ id = "vma_l4_full_matrix"; script = "scripts/smoke-vm-acceptance-matrix.ps1 -Level L4"; note = "parallel evidence bundle spec 029" },
        @{ id = "lean_stack"; script = "scripts/smoke-lean-stack.sh"; note = "alternate topology" },
        @{ id = "ui_a11y_manual"; note = "Cursor browser a11y audit spec 026 P5" },
        @{ id = "stage_prod_ops"; registry = "specs/015-live-server-ops-backlog" }
    )
}

$doc | ConvertTo-Json -Depth 6 | Set-Content -Path $OutPath -Encoding utf8
$latest = Join-Path $EvDir "vpp-fortress-gap-latest.json"
Copy-Item -Path $OutPath -Destination $latest -Force

Write-Host ""
Write-Host "Fortress gap report: $($fortress.Count) gates -> 160%, $($excluded.Count) policy exclusions" -ForegroundColor Cyan
Write-Host "Report: $OutPath" -ForegroundColor DarkGray
Write-Output $OutPath
