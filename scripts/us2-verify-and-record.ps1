param(
    [switch]$RunSmoke,
    [switch]$AutoUp,
    [switch]$AutoDown,
    [switch]$SkipHotPlug,
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

function Get-GitValue([string]$args) {
    try {
        $value = (git $args 2>$null)
        if ($LASTEXITCODE -eq 0 -and $value) {
            return ($value | Select-Object -First 1).ToString().Trim()
        }
    } catch {
    }
    return "unknown"
}

function New-DefaultOutputPath {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    return Join-Path $repoRoot "docs/review/us2-verification-run-$stamp.md"
}

if (-not $OutputPath) {
    $OutputPath = New-DefaultOutputPath
}

$startedAt = Get-Date
$branch = Get-GitValue "rev-parse --abbrev-ref HEAD"
$commit = Get-GitValue "rev-parse --short HEAD"
$operator = $env:USERNAME
if (-not $operator) { $operator = "unknown" }

$smokeStatus = "not-run"
$smokeError = ""

if ($RunSmoke) {
    try {
        $smokeArgs = @{}
        if ($AutoUp) { $smokeArgs["AutoUp"] = $true }
        if ($AutoDown) { $smokeArgs["AutoDown"] = $true }
        if ($SkipHotPlug) { $smokeArgs["SkipHotPlug"] = $true }
        & (Join-Path $scriptDir "smoke-us2-epic01.ps1") @smokeArgs
        if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
            throw "smoke-us2-epic01.ps1 exited with code $LASTEXITCODE"
        }
        $smokeStatus = "passed"
    } catch {
        $smokeStatus = "failed"
        $smokeError = $_.Exception.Message
    }
}

$lines = @()
$lines += "# US2 Verification Run"
$lines += ""
$lines += "| Field | Value |"
$lines += "|---|---|"
$lines += "| Started at | $($startedAt.ToString('yyyy-MM-dd HH:mm:ss zzz')) |"
$lines += "| Operator | $operator |"
$lines += "| Branch | $branch |"
$lines += "| Commit | $commit |"
$lines += "| Run smoke | $RunSmoke |"
$lines += "| Smoke status | $smokeStatus |"
$lines += "| AutoUp | $AutoUp |"
$lines += "| AutoDown | $AutoDown |"
$lines += "| SkipHotPlug | $SkipHotPlug |"
$lines += ""

if ($smokeError) {
    $lines += "## Smoke Error"
    $lines += ""
    $lines += '```text'
    $lines += $smokeError
    $lines += '```'
    $lines += ""
}

$lines += "## Task Evidence Checklist"
$lines += ""
$lines += "- T023 Solr atomic update: pending"
$lines += "- T024 Prometheus metrics: pending"
$lines += "- T025 Chunked deep-archive objects: pending"
$lines += "- T026 File-ref skip behavior: pending"
$lines += "- T028 Epic checkbox sweep: pending"
$lines += "- T047 Phase B status completed: pending"
$lines += ""
$lines += "Fill this run output together with:"
$lines += "- docs/review/us2-verification-template-2026-05-23.md"
$lines += "- docs/review/us2-runtime-runbook-2026-05-23.md"

$outDir = Split-Path -Parent $OutputPath
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

Set-Content -Path $OutputPath -Value $lines -Encoding UTF8
Write-Host "[OK] US2 verification run file: $OutputPath" -ForegroundColor Green

if ($RunSmoke -and $smokeStatus -eq "failed") {
    exit 1
}
exit 0
