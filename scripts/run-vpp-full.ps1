#Requires -Version 5.1
<#
.SYNOPSIS
  Vseobemlyayushchaya proverka produkta (VPP) - spec 030 orchestrator.

.DESCRIPTION
  Comprehensive zero-SKIP VPP (145 lab gates). Use -UntilGreen to repeat full complex after fix until all gates PASS.

  On failure: writes vpp-failure-analysis.json with remediation hints.
  Policy: analyze -> fix -> retry -> repeat ENTIRE VPP until GREEN.

.EXAMPLE
  .\scripts\run-vpp-full.ps1 -Level standard -UntilGreen
  .\scripts\run-vpp-until-green.ps1 -Level full
#>
param(
    [ValidateSet('quick', 'standard', 'full')]
    [string]$Level = 'full',
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [switch]$SkipBuild,
    [switch]$SkipIntegrations,
    [switch]$SkipPlaywright,
    [switch]$SkipLoad,
    [switch]$UntilGreen,
    [int]$MaxAttempts = 10,
    [switch]$PauseOnFail,
    [string]$ResumeCheckpoint = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $Root "scripts\vpp\Invoke-VppFullRun.ps1")
$writeFailure = Join-Path $Root "scripts\Write-VppFailureAnalysis.ps1"

if ($Help) {
    Write-Host @"
Usage: .\scripts\run-vpp-full.ps1 [-Level quick|standard|full] [-UntilGreen] [-MaxAttempts 10]

  Vseobemlyayushchaya proverka produkta (VPP, spec 030).
Prerequisite: .\scripts\qemu-up.ps1 [-WithIntegrations]

Levels:
  full      ~8-16 h — CANONICAL comprehensive (145 gates, zero SKIP, full_coverage=true)
  standard  ~2-3 h — subsample (not product-complete)
  quick     ~30-45 min — smoke subset only

-UntilGreen
  Repeat ENTIRE complex after each failure until all gates PASS or MaxAttempts reached.
  On failure: analyze problem (vpp-failure-analysis.json), fix, then auto-retry.

-PauseOnFail
  With -UntilGreen: wait for Enter between attempts (operator fix window).

Failure policy (mandatory):
  1. Capture logs (not last line only)
  2. Diagnose root cause
  3. Fix (minimal diff / stack restart)
  4. Verify isolated gate or resume from checkpoint
  5. On GREEN with checkpoint: one final full pass from gate 1

Evidence: deploy/qemu/run/vpp-evidence/
Failure:  deploy/qemu/run/vpp-failure-analysis.json

Live monitoring (faster than 5 min):
  .\scripts\vpp\Start-VppStatusWatcher.ps1          # default 60s ticks
  deploy\qemu\run\stack-wait-live.json              # stack/maintenance wait state
Env: VPP_STATUS_TICK_SEC, KORUS_STACK_WAIT_INTERVAL_SEC (45),
     KORUS_STACK_BUSY_INTERVAL_SEC (15), KORUS_STACK_MAX_MAINTENANCE_MIN (20 fail-fast)
"@
    exit 0
}

function Invoke-VppAttempt {
    param([int]$Attempt, [string]$CheckpointPath = "")
    $runArgs = @{
        Level = $Level; ApiBaseUrl = $ApiBaseUrl; WebBaseUrl = $WebBaseUrl
        SkipBuild = $SkipBuild; SkipIntegrations = $SkipIntegrations
        SkipPlaywright = $SkipPlaywright; SkipLoad = $SkipLoad; Attempt = $Attempt
    }
    if ($CheckpointPath) { $runArgs['ResumeCheckpoint'] = $CheckpointPath }
    return Invoke-VppFullRun @runArgs
}

$checkpointForRun = $ResumeCheckpoint
if (-not $checkpointForRun -and $env:VPP_RESUME_CHECKPOINT) { $checkpointForRun = $env:VPP_RESUME_CHECKPOINT }

if (-not $UntilGreen) {
    $result = Invoke-VppAttempt -Attempt 1 -CheckpointPath $checkpointForRun
    if (-not $result.Ok) {
        & $writeFailure -Attempt 1 -MaxAttempts 1 -Level $Level -Gates $result.Gates `
            -Dimensions $result.Dimensions -LastFailedGate $result.LastFailedGate `
            -LastExitCode $result.LastExitCode | Out-Null
        Write-Host ""
        Write-Host "Re-run with -UntilGreen after fix, or: .\scripts\run-vpp-until-green.ps1 -Level $Level" -ForegroundColor Yellow
        exit $result.LastExitCode
    }
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ('  VPP {0} GREEN ({1} sec)' -f $Level, $result.DurationSec) -ForegroundColor Green
    Write-Host "  Evidence: $($result.EvidencePath)" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "VPP UntilGreen: fix -> resume from checkpoint -> repeat until GREEN; then one full verify pass" -ForegroundColor Cyan
$retryDelay = 120
if ($env:VPP_RETRY_DELAY_SEC) {
    $parsed = 0
    if ([int]::TryParse($env:VPP_RETRY_DELAY_SEC, [ref]$parsed) -and $parsed -gt 0) { $retryDelay = $parsed }
}
Write-Host "Retry delay between attempts: ${retryDelay}s (set VPP_RETRY_DELAY_SEC to override)" -ForegroundColor DarkGray
$checkpointPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-checkpoint.json"
$saveCheckpoint = Join-Path $Root "scripts\vpp\Save-VppCheckpoint.ps1"
$usedResume = [bool]$checkpointForRun

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    if ($attempt -gt 1) {
        Write-Host ""
        Write-Host "--- VPP resume attempt $attempt/$MaxAttempts ---" -ForegroundColor Magenta
    }

    $cp = $checkpointForRun
    if (-not $cp -and (Test-Path $checkpointPath)) { $cp = $checkpointPath; $usedResume = $true }
    if ($cp) { $usedResume = $true }
    $result = Invoke-VppAttempt -Attempt $attempt -CheckpointPath $cp

    if ($result.Ok) {
        if ($usedResume -and $env:VPP_SKIP_FINAL_VERIFY -ne '1') {
            Write-Host ""
            Write-Host "=== Final verification: full pass from gate 1 (no checkpoint) ===" -ForegroundColor Cyan
            if (Test-Path $checkpointPath) {
                $bak = "$checkpointPath.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
                Move-Item -LiteralPath $checkpointPath -Destination $bak -Force -ErrorAction SilentlyContinue
            }
            Remove-Item Env:VPP_RESUME_CHECKPOINT -ErrorAction SilentlyContinue
            $verify = Invoke-VppAttempt -Attempt ($attempt + 1) -CheckpointPath ""
            if (-not $verify.Ok) {
                Write-Host "[FAIL] Final full verify pass failed at $($verify.LastFailedGate)" -ForegroundColor Red
                if (Test-Path $saveCheckpoint) {
                    & $saveCheckpoint -Reason "final verify FAIL: $($verify.LastFailedGate)" -ResumeGate $verify.LastFailedGate | Out-Null
                }
                exit $verify.LastExitCode
            }
            $result = $verify
        }

        $greenPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-green.json"
        $gatePass = @($result.Gates.Values | Where-Object { $_ -eq "PASS" }).Count
        $gateTotal = @($result.Gates.Keys | Where-Object { $_ -notin @("overall_status", "attempt", "coverage_report") }).Count
        @{
            spec = "030-vpp-product-verification"
            status = "GREEN"
            level = $Level
            full_coverage = $true
            gates_pass = $gatePass
            gates_total = $gateTotal
            attempts = $attempt
            timestamp = (Get-Date).ToUniversalTime().ToString("o")
            evidence = $result.EvidencePath
            coverage = $result.CoveragePath
            manifest = "specs/030-vpp-product-verification/contracts/vpp-comprehensive-gates.json"
        } | ConvertTo-Json | Set-Content -Path $greenPath -Encoding utf8

        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ('  VPP {0} GREEN after {1} attempt(s) ({2} sec)' -f $Level, $attempt, $result.DurationSec) -ForegroundColor Green
        Write-Host "  Evidence: $($result.EvidencePath)" -ForegroundColor Green
        Write-Host "  Marker:   $greenPath" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        exit 0
    }

    $analysisPath = & $writeFailure -Attempt $attempt -MaxAttempts $MaxAttempts -Level $Level `
        -Gates $result.Gates -Dimensions $result.Dimensions `
        -LastFailedGate $result.LastFailedGate -LastExitCode $result.LastExitCode

    if (Test-Path $saveCheckpoint) {
        try {
            & $saveCheckpoint -Reason "until-green attempt $attempt FAIL" -ResumeGate $result.LastFailedGate | Out-Null
            $checkpointForRun = $checkpointPath
            $usedResume = $true
        } catch { Write-Host "[warn] checkpoint save: $_" -ForegroundColor DarkYellow }
    }

    if ($attempt -ge $MaxAttempts) {
        Write-Host ""
        Write-Host "[FAIL] VPP not GREEN after $MaxAttempts attempts. Fix blockers and re-run -UntilGreen." -ForegroundColor Red
        exit $result.LastExitCode
    }

    if ($PauseOnFail) {
        Write-Host ""
        Write-Host "Fix the issue, then press Enter to resume VPP from checkpoint..." -ForegroundColor Yellow
        Read-Host | Out-Null
    } else {
        Write-Host ""
        Write-Host "Next: fix per analysis, then resume from checkpoint in ${retryDelay}s..." -ForegroundColor Yellow
        Start-Sleep -Seconds $retryDelay
    }
}

exit 1
