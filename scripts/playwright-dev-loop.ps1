# US9 inner loop: preflight + tiered Playwright against live QEMU stack (host browser OK).
param(
    [ValidateSet('api', 'ui-auth', 'ui-mobile', 'ui-visual', 'ui-visual-regression', 'ui-conversation', 'ui-messaging', 'ui-files', 'ui-conference', 'ui-call-flows', 'ui-live', 'ui-e2ee', 'ui-bot', 'ui-admin', 'ui-admin-extended', 'ui-interaction-audit', 'ui-branding', 'ui-i18n-artifacts', 'ui-avatar', 'ui-tests', 'e2ee-openmls-interop', 'full', 'all-inner')]
    [string]$Tier = 'api',
    [switch]$SkipPreflight,
    [switch]$WaitForStack,
    [int]$WaitTimeoutMinutes = 90,
    [int]$WaitIntervalSec = 180,
    [switch]$SyncWebUi,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\playwright-dev-loop.ps1 [-Tier api|...] [-SyncWebUi] [-WaitForStack] [-WaitTimeoutMinutes 90]

Fast Playwright against http://127.0.0.1:19088 / :18080 (QEMU must be up).
-WaitForStack (default ON): poll VM/health until ready (ping every 3 min by default).
-SkipPreflight skips wait and health checks (offline debug only).
-SyncWebUi runs qemu-web-sync.ps1 first (~10s, requires hotswap enabled).
Updates deploy/qemu/run/inner-tier-status.json and plan-failure-analysis.json on failure.

Examples:
  .\scripts\playwright-dev-loop.ps1 -Tier api
  .\scripts\playwright-dev-loop.ps1 -Tier ui-visual
  .\scripts\playwright-dev-loop.ps1 -Tier ui-conversation
  .\scripts\playwright-dev-loop.ps1 -Tier ui-call-flows
  .\scripts\playwright-dev-loop.ps1 -Tier ui-admin-extended
  .\scripts\playwright-dev-loop.ps1 -Tier ui-interaction-audit
  .\scripts\playwright-dev-loop.ps1 -Tier ui-branding
  .\scripts\playwright-dev-loop.ps1 -Tier ui-i18n-artifacts
  .\scripts\playwright-dev-loop.ps1 -Tier ui-tests
  .\scripts\playwright-dev-loop.ps1 -Tier all-inner
  .\scripts\playwright-dev-loop.ps1 -Tier full   # outer gate (all specs)
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
if (-not (Test-Path $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }

. (Join-Path $Root "deploy\qemu\lib\Invoke-KorusPlanFailureAnalysis.ps1")
. (Join-Path $Root "deploy\qemu\lib\Get-KorusInnerTierStatus.ps1")
. (Join-Path $Root "deploy\qemu\lib\Wait-KorusPlanPlaywrightStack.ps1")

$doWait = $WaitForStack.IsPresent -or (-not $SkipPreflight -and -not $PSBoundParameters.ContainsKey('WaitForStack'))

$env:KORUS_WEB_URL = "http://127.0.0.1:19088"
$env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
$env:KORUS_API_URL = "http://127.0.0.1:18080"

function Invoke-TierPlaywright {
    param(
        [string]$TierName,
        [object]$TierDef
    )
    $e2e = Join-Path $Root "tests\e2e-web"
    $log = Join-Path $RunDir "playwright-dev-loop.log"
    Push-Location $e2e
    try {
        if (-not (Test-Path node_modules)) {
            Write-Host "npm ci..."
            npm ci
            if ($LASTEXITCODE -ne 0) { return $false }
        }
        $pwArgs = @("playwright", "test")
        if ($TierDef.args -and @($TierDef.args).Count -gt 0) {
            $pwArgs += @($TierDef.args)
        }
        if ($TierDef.grep) {
            $pwArgs += "--grep=$([string]$TierDef.grep)"
        }
        Write-Host "--- tier=$TierName npx $($pwArgs -join ' ') ---"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            # Node/Playwright write warnings to stderr; PS 5.1 surfaces them as ErrorRecord (red noise).
            & npx @pwArgs 2>&1 | ForEach-Object {
                $line = if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { "$_" }
                if ($line -match '(?i)NO_COLOR.*FORCE_COLOR') { return }
                if ($_ -is [System.Management.Automation.ErrorRecord]) { Write-Host $line } else { Write-Output $_ }
            } | Tee-Object -FilePath $log | Out-Host
            $exit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prevEap
        }
        return ($exit -eq 0)
    } finally {
        Pop-Location
    }
}

if ($SyncWebUi) {
    $syncScript = Join-Path $Root "scripts\qemu-web-sync.ps1"
    if (-not (Test-Path $syncScript)) {
        Write-Error "missing $syncScript"
    }
    . (Join-Path $Root "deploy\qemu\lib\Test-KorusWebHotswap.ps1")
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    . (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")
    if (Test-KorusQemuStackRunning -RunDir $RunDir) {
        $whk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
        if ($whk -and -not (Test-KorusGuestWebHotswapActive -HostKey $whk)) {
            Write-Host "[!!] hotswap off - run: .\scripts\qemu-dev-mode.ps1 -Mode enable-hotswap" -ForegroundColor Yellow
        }
    }
    Write-Host "SyncWebUi: qemu-web-sync.ps1" -ForegroundColor Cyan
    & $syncScript
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $SkipPreflight) {
    try {
        if ($doWait) {
            Write-Host "Waiting for QEMU stack (timeout ${WaitTimeoutMinutes}m, interval ${WaitIntervalSec}s)..." -ForegroundColor Cyan
            $pre = Wait-KorusPlanPlaywrightStack -Root $Root -RunDir $RunDir `
                -TimeoutMinutes $WaitTimeoutMinutes -IntervalSec $WaitIntervalSec
        } else {
            $pre = Test-KorusPlanPlaywrightPreflight -Root $Root -RunDir $RunDir
            if (-not $pre.Ok) {
                Write-Host 'FAIL preflight:' -ForegroundColor Red
                $pre.Issues | ForEach-Object { Write-Host "  $_" }
                $analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root `
                    -LastError ("preflight: " + ($pre.Issues -join "; "))
                if ($Tier -ne 'all-inner') {
                    Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $Tier -Pass $false `
                        -LastError $analysis.summaryRu | Out-Null
                }
                exit 1
            }
        }
        Write-Host "OK preflight web=$($pre.WebUrl) api=$($pre.ApiUrl)" -ForegroundColor Green
    } catch {
        Write-Host "FAIL stack wait: $($_.Exception.Message)" -ForegroundColor Red
        $analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root `
            -LastError $_.Exception.Message
        if ($Tier -ne 'all-inner') {
            Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $Tier -Pass $false `
                -LastError $analysis.summaryRu | Out-Null
        }
        exit 1
    }
}

$manifest = Get-KorusPlaywrightTiersManifest -Root $Root

if ($Tier -eq 'all-inner') {
    $seq = @($manifest.tiers.'all-inner'.sequential)
    $failed = @()
    foreach ($t in $seq) {
        $def = $manifest.tiers.$t
        $ok = Invoke-TierPlaywright -TierName $t -TierDef $def
        if ($ok) {
            Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $t -Pass $true | Out-Null
            Write-Host "OK tier $t" -ForegroundColor Green
        } else {
            $analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root -LastError "tier=$t"
            Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $t -Pass $false -LastError $analysis.summaryRu | Out-Null
            Write-Host "FAIL tier $t - $($analysis.summaryRu)" -ForegroundColor Red
            $failed += $t
            break
        }
    }
    if ($failed.Count -gt 0) { exit 1 }
    Write-Host 'OK all inner tiers pass' -ForegroundColor Green
    exit 0
}

if ($Tier -eq 'full') {
    Write-Host "Outer gate: full Playwright suite (all specs)." -ForegroundColor Cyan
    $fullDef = @{ args = @(); grep = $null }
    $ok = Invoke-TierPlaywright -TierName 'full' -TierDef $fullDef
    if ($ok) {
        Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName 'full' -Pass $true | Out-Null
        Write-Host 'OK tier full (outer gate)' -ForegroundColor Green
        exit 0
    }
    $analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root -LastError 'tier=full'
    Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName 'full' -Pass $false -LastError $analysis.summaryRu | Out-Null
    Write-Host "FAIL tier full - $($analysis.summaryRu)" -ForegroundColor Red
    exit 1
}

$tierDef = $manifest.tiers.$Tier
if (-not $tierDef) {
    Write-Error "unknown tier $Tier"
    exit 2
}

if ($Tier -eq 'ui-tests' -and -not $env:UI_TESTS_PROFILE) {
    $env:UI_TESTS_PROFILE = 'smoke'
}
if ($Tier -eq 'ui-ux-smoke') { $env:UI_TESTS_PROFILE = 'smoke' }
if ($Tier -eq 'ui-ux-pr') { $env:UI_TESTS_PROFILE = 'pr' }
if ($Tier -eq 'ui-ux-full') { $env:UI_TESTS_PROFILE = 'full' }

$ok = Invoke-TierPlaywright -TierName $Tier -TierDef $tierDef
if ($ok) {
    Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $Tier -Pass $true | Out-Null
    Write-Host "OK tier $Tier" -ForegroundColor Green
    exit 0
}

$analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root -LastError "tier=$Tier"
Set-KorusInnerTierResult -RunDir $RunDir -Root $Root -TierName $Tier -Pass $false -LastError $analysis.summaryRu | Out-Null
Write-Host "FAIL $($analysis.summaryRu)" -ForegroundColor Red
Write-Host "See deploy/qemu/run/plan-failure-analysis.json" -ForegroundColor DarkGray
exit 1
