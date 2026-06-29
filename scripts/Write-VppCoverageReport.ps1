#Requires -Version 5.1

# VPP coverage analysis — comprehensive zero-SKIP manifest (spec 030).

param(

    [hashtable]$Gates = @{},

    [string]$ManifestPath = "",

    [string]$OutPath = "",

    [switch]$Help

)



$ErrorActionPreference = "Stop"

if ($Help) {

    Write-Host "Usage: .\scripts\Write-VppCoverageReport.ps1 -Gates `$gates"

    exit 0

}



$Root = Split-Path -Parent $PSScriptRoot

if (-not $ManifestPath) {

    $ManifestPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json"

}

$manifest = Get-Content -Raw $ManifestPath | ConvertFrom-Json



$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"

if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

if (-not $OutPath) {

    $OutPath = Join-Path $EvDir ("vpp-coverage-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")

}



$ordered = @($manifest.comprehensive_gates_ordered | Where-Object { $_ -ne "coverage_report" })

$passed = 0; $failed = 0; $skipped = 0; $notRun = 0

$gateResults = @{}

$gaps = @()



foreach ($g in $ordered) {

    $status = if ($Gates.ContainsKey($g)) { $Gates[$g] } else { "NOT_RUN" }

    $gateResults[$g] = $status

    switch ($status) {

        "PASS" { $passed++ }

        "FAIL" { $failed++ }

        "SKIP" { $skipped++ }

        default { $notRun++ }

    }

    if ($status -ne "PASS") {

        $dim = if ($manifest.gates.$g.dimension) { $manifest.gates.$g.dimension } else { "?" }

        $gaps += @{ gate = $g; status = $status; dimension = $dim; mandatory = $true }

    }

}



$total = $ordered.Count

$pct = if ($total -gt 0) { [math]::Round(($passed / $total) * 1000) / 10 } else { 100 }

$fullCoverage = ($failed -eq 0 -and $skipped -eq 0 -and $notRun -eq 0)



$coverageReportStatus = if ($Gates.ContainsKey("coverage_report")) { $Gates.coverage_report } else { "NOT_RUN" }

if ($coverageReportStatus -ne "PASS" -and $fullCoverage) {

    $fullCoverage = $false

}



$doc = [ordered]@{

    spec = "030-vpp-product-verification"

    title = "VPP comprehensive coverage report"

    timestamp = (Get-Date).ToUniversalTime().ToString("o")

    summary = @{

        mode = $manifest.coverage_policy.mode

        gates_total = $total

        gates_pass = $passed

        gates_fail = $failed

        gates_skip = $skipped

        gates_not_run = $notRun

        coverage_pct = $pct

        full_coverage = $fullCoverage

    }

    gates = $gateResults

    gaps = $gaps

    excluded_live_server_only = @($manifest.requires_live_server_spec_015_only)

    manifest = "specs/030-vpp-product-verification/contracts/vpp-comprehensive-gates.json"

    policy = "comprehensive_zero_skip: every lab gate PASS, zero SKIP/NOT_RUN"

}



$doc | ConvertTo-Json -Depth 8 | Set-Content -Path $OutPath -Encoding utf8

$latest = Join-Path $EvDir "vpp-coverage-latest.json"

Copy-Item -Path $OutPath -Destination $latest -Force



Write-Host ""

Write-Host ('VPP comprehensive: {0}/{1} PASS ({2}%) full_coverage={3}' -f $passed, $total, $pct, $fullCoverage) -ForegroundColor $(if ($fullCoverage) { "Green" } else { "Yellow" })

if ($gaps.Count -gt 0) {

    Write-Host "Gaps ($($gaps.Count)):" -ForegroundColor Yellow

    foreach ($gap in $gaps) {

        Write-Host "  [$($gap.status)] $($gap.dimension):$($gap.gate)" -ForegroundColor DarkYellow

    }

}

Write-Host "Report: $OutPath" -ForegroundColor Cyan



if (-not $fullCoverage) { exit 1 }

Write-Output $OutPath

