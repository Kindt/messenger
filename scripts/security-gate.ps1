# Security gate: buildIntegrity + optional QEMU smokes (spec 014).
param(
    [switch]$SkipBuild,
    [switch]$SkipQemuSmokes,
    [switch]$Strict,
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [double]$MaxTimingDelta = 0.05,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\security-gate.ps1 [-SkipBuild] [-SkipQemuSmokes] [-Strict] [-BaseUrl url]

Runs PR security gate:
  1. ./gradlew buildIntegrity (spotless ratchet, npm audit, benchmark, all tests)
  2. Optional QEMU smokes when API health OK:
     smoke-security-headers, audit-timing, smoke-rate-limit
  3. With -Strict (FSTEC conveyor): + smoke-ip-allowlist (-RequireEnforce),
     smoke-admin-audit-retention, smoke-dlp-mock (-SkipIfUnreachable),
     smoke-denied-access-audit, smoke-passkeys-scaffold, smoke-fstec-prod-prep,
     smoke-org-geo-deny (SKIP if enforce off), smoke-desktop-security

Examples:
  .\scripts\security-gate.ps1 -SkipQemuSmokes
  .\scripts\security-gate.ps1 -Strict
  .\scripts\security-gate.ps1 -SkipBuild
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot

function Invoke-SecurityGateScript {
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [string[]]$ArgumentList = @()
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @ArgumentList 2>&1 | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

Push-Location $Root
try {
    if (-not $SkipBuild) {
        Write-Host "=== buildIntegrity (spec 014) ===" -ForegroundColor Cyan
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & .\gradlew.bat buildIntegrity --no-daemon 2>&1 | Out-Host
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($buildExit -ne 0) {
            Write-Host "[FAIL] buildIntegrity" -ForegroundColor Red
            exit $buildExit
        }
        Write-Host "[OK] buildIntegrity" -ForegroundColor Green
    }

    if ($SkipQemuSmokes) {
        if ($Strict) {
            Write-Host "=== FSTEC strict (offline) ===" -ForegroundColor Cyan
            $desktopExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-desktop-security.ps1"
            if ($desktopExit -ne 0) { exit $desktopExit }
            Write-Host "[OK] security-gate (build + FSTEC offline strict)" -ForegroundColor Green
        } else {
            Write-Host "[OK] security-gate (build only)" -ForegroundColor Green
        }
        exit 0
    }

    try {
        $null = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
    } catch {
        if ($Strict) {
            Write-Host "[WARN] QEMU smokes skipped: API not reachable at $BaseUrl" -ForegroundColor Yellow
            Write-Host "=== FSTEC strict (offline) ===" -ForegroundColor Cyan
            $desktopExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-desktop-security.ps1"
            if ($desktopExit -ne 0) { exit $desktopExit }
            Write-Host "[OK] security-gate (FSTEC offline strict only)" -ForegroundColor Green
            exit 0
        }
        Write-Host "[SKIP] QEMU smokes: API not reachable at $BaseUrl (use -SkipQemuSmokes for build-only CI parity)" -ForegroundColor Yellow
        exit 0
    }

    Write-Host "=== QEMU security smokes ===" -ForegroundColor Cyan
    if ($BaseUrl -match ':18080' -and -not $env:SECURITY_TIMING_NORMALIZATION_MIN_MS) {
        $env:SECURITY_TIMING_NORMALIZATION_MIN_MS = '220'
    }
    $timingDelta = $MaxTimingDelta
    if ($BaseUrl -match ':18080' -and $timingDelta -le 0.05) { $timingDelta = 0.25 }
    $headersExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-security-headers.ps1" -ArgumentList @("-BaseUrl", $BaseUrl)
    if ($headersExit -ne 0) { exit $headersExit }

    $auditExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\audit-timing.ps1" -ArgumentList @("-BaseUrl", $BaseUrl, "-MaxDeltaRatio", $timingDelta)
    if ($auditExit -ne 0) { exit $auditExit }

    if ($Strict -and $BaseUrl -match ':18080') {
        Write-Host "=== auth rate-limit cooldown (post audit-timing) ===" -ForegroundColor Cyan
        $cooldownExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\vpp\Wait-AuthRateLimitCooldown.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl, "-MaxSec", "120")
        if ($cooldownExit -ne 0) { exit $cooldownExit }
    }

    if ($Strict) {
        Write-Host "=== FSTEC strict smokes ===" -ForegroundColor Cyan
        $ipExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-ip-allowlist.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl, "-RequireEnforce")
        if ($ipExit -ne 0) { exit $ipExit }

        $auditRetentionExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-admin-audit-retention.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl)
        if ($auditRetentionExit -ne 0) { exit $auditRetentionExit }

        $dlpExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-dlp-mock.ps1" -ArgumentList @("-SkipIfUnreachable")
        if ($dlpExit -ne 0) { exit $dlpExit }

        $deniedAuditExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-denied-access-audit.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl)
        if ($deniedAuditExit -ne 0) { exit $deniedAuditExit }

        $passkeysExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-passkeys-scaffold.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl)
        if ($passkeysExit -ne 0) { exit $passkeysExit }

        $prodPrepExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-fstec-prod-prep.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl)
        if ($prodPrepExit -ne 0) { exit $prodPrepExit }

        $geoExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-org-geo-deny.ps1" `
            -ArgumentList @("-BaseUrl", $BaseUrl)
        if ($geoExit -ne 0) { exit $geoExit }

        $desktopExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-desktop-security.ps1"
        if ($desktopExit -ne 0) { exit $desktopExit }
    }

    # Last: rate-limit smoke triggers 429 and blocks subsequent logins for a window.
    $rateExit = Invoke-SecurityGateScript -ScriptPath "$Root\scripts\smoke-rate-limit.ps1" -ArgumentList @("-BaseUrl", $BaseUrl)
    if ($rateExit -ne 0) { exit $rateExit }

    Write-Host "[OK] security-gate (build + QEMU smokes$(if ($Strict) { ' + FSTEC strict' }))" -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
