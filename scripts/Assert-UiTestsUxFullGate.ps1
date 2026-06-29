#Requires -Version 5.1
# Post-run gate for ui-ux-full: UX rubric strict + CLK coverage floor (spec 026/030).
param(
    [string]$SummaryPath = "",
    [int]$MinClickCoveragePct = 0,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\Assert-UiTestsUxFullGate.ps1 [-SummaryPath path] [-MinClickCoveragePct 70]

Fails if ui-ux-full summary missing, has failures, or click-coverage below floor.
Reads: tests/e2e-web/artifacts/ux-runs/summary.json
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
if (-not $SummaryPath) {
    $SummaryPath = Join-Path $Root "tests\e2e-web\artifacts\ux-runs\summary.json"
}
if (-not (Test-Path $SummaryPath)) {
    Write-Host "[FAIL] missing ui-tests summary: $SummaryPath" -ForegroundColor Red
    exit 1
}

$summary = Get-Content -Raw $SummaryPath | ConvertFrom-Json
if ($summary.profile -ne "full") {
    Write-Host "[FAIL] expected profile=full, got $($summary.profile)" -ForegroundColor Red
    exit 1
}

$minPct = if ($MinClickCoveragePct -gt 0) { $MinClickCoveragePct }
          elseif ($summary.uxClkMinPct) { [int]$summary.uxClkMinPct }
          else { 70 }

if ($summary.counts.failed -gt 0) {
    Write-Host "[FAIL] ui-ux-full: $($summary.counts.failed) scenario failures" -ForegroundColor Red
    exit 1
}

$cov = $summary.clickCoverage.coveragePct
if ($null -eq $cov) {
    Write-Host "[FAIL] ui-ux-full: clickCoverage missing in summary" -ForegroundColor Red
    exit 1
}
if ([double]$cov -lt $minPct) {
    Write-Host "[FAIL] ui-ux-full CLK coverage $cov% < minimum $minPct%" -ForegroundColor Red
    exit 1
}

if ($summary.uxStrict -ne $true) {
    Write-Host "[FAIL] ui-ux-full: uxStrict not set (UI_TESTS_UX_STRICT required)" -ForegroundColor Red
    exit 1
}

Write-Host ("[OK] ui-ux-full UX gate: {0}/{1} pass, CLK {2}% (min {3}%), uxStrict=true" -f `
    $summary.counts.passed, $summary.counts.total, $cov, $minPct) -ForegroundColor Green
