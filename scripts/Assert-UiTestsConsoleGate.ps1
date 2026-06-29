#Requires -Version 5.1
# Post-run gate for ui-ux-full: browser console guard evidence (spec 030).
param(
    [string]$SummaryPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\Assert-UiTestsConsoleGate.ps1 [-SummaryPath path]

Fails if ui-ux-full summary missing console guard evidence or console issues recorded.
Reads: tests/e2e-web/artifacts/ux-runs/summary.json
       deploy/qemu/run/ui-console-guard-report.json
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

if (-not $summary.consoleGuard) {
    Write-Host "[FAIL] ui-ux-full: consoleGuard missing in summary (UI_CONSOLE_GUARD / console-guard.ts)" -ForegroundColor Red
    exit 1
}

$cg = $summary.consoleGuard
if ($cg.enabled -ne $true) {
    Write-Host "[FAIL] ui-ux-full: console guard disabled (set UI_CONSOLE_GUARD=1, not 0)" -ForegroundColor Red
    exit 1
}

if ($summary.counts.failed -gt 0) {
    Write-Host "[FAIL] ui-ux-full: $($summary.counts.failed) scenario failures (may include console guard)" -ForegroundColor Red
    if ($cg.topIssues -and $cg.topIssues.Count -gt 0) {
        Write-Host "Top console issues:" -ForegroundColor Yellow
        foreach ($issue in $cg.topIssues | Select-Object -First 5) {
            Write-Host "  [$($issue.count)x] $($issue.key)" -ForegroundColor DarkYellow
        }
    }
    exit 1
}

if ($cg.scenarioFailures -gt 0 -or $cg.totalOccurrences -gt 0) {
    Write-Host "[FAIL] ui-ux-full: console issues recorded (failures=$($cg.scenarioFailures), occurrences=$($cg.totalOccurrences))" -ForegroundColor Red
    exit 1
}

Write-Host ("[OK] ui-ux-full console gate: guard enabled, 0 console issues across {0} scenarios" -f $summary.counts.total) -ForegroundColor Green
