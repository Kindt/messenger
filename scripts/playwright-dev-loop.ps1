# US9 inner loop: preflight + tiered Playwright against live QEMU stack (host browser OK).
param(
    [ValidateSet('api', 'ui-auth', 'ui-mobile', 'ui-visual', 'ui-visual-regression', 'ui-conversation', 'ui-messaging', 'ui-messaging-extended', 'ui-files', 'ui-conference', 'ui-call-flows', 'ui-live', 'ui-e2ee', 'ui-bot', 'ui-admin', 'ui-admin-extended', 'ui-interaction-audit', 'ui-branding', 'ui-shell-layouts', 'ui-i18n-artifacts', 'ui-avatar', 'ui-push', 'ui-tests', 'ui-ux-smoke', 'ui-ux-pr', 'ui-ux-full', 'vpp-ui-blocks', 'e2ee-openmls-interop', 'full', 'all-inner', 'all-inner-core')]
    [string]$Tier = 'api',
    [switch]$SkipPreflight,
    [switch]$WaitForStack,
    [int]$WaitTimeoutMinutes = 0,
    [int]$WaitIntervalSec = 0,
    [int]$WaitBusyIntervalSec = 0,
    [int]$MaxMaintenanceMinutes = 0,
    [switch]$SyncWebUi,
    [int]$StartAfterTestIndex = 0,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\playwright-dev-loop.ps1 [-Tier api|...] [-SyncWebUi] [-WaitForStack]

Fast Playwright against http://127.0.0.1:19088 / :18080 (QEMU must be up).
-WaitForStack (default ON): poll VM/health until ready (default every 45s; maintenance every 15s).
-SkipPreflight skips wait and health checks (offline debug only).
Env: KORUS_STACK_WAIT_INTERVAL_SEC, KORUS_STACK_BUSY_INTERVAL_SEC, KORUS_STACK_MAX_MAINTENANCE_MIN (default 20, fail-fast on hung redeploy lock).
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
. (Join-Path $Root "deploy\qemu\lib\Get-KorusStackWaitParams.ps1")
. (Join-Path $Root "deploy\qemu\lib\Wait-KorusPlanPlaywrightStack.ps1")

$waitParams = Get-KorusStackWaitParams -TimeoutMinutes $WaitTimeoutMinutes -IntervalSec $WaitIntervalSec `
    -BusyIntervalSec $WaitBusyIntervalSec -MaxMaintenanceMinutes $MaxMaintenanceMinutes

$doWait = $WaitForStack.IsPresent -or (-not $SkipPreflight -and -not $PSBoundParameters.ContainsKey('WaitForStack'))

$env:KORUS_WEB_URL = "http://127.0.0.1:19088"
$env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
$env:KORUS_API_URL = "http://127.0.0.1:18080"

function Invoke-TierPlaywright {
    param(
        [string]$TierName,
        [object]$TierDef,
        [int]$ResumeAfterIndex = 0
    )
    $e2e = Join-Path $Root "tests\e2e-web"
    $log = Join-Path $RunDir "playwright-dev-loop.log"
    $prevStartAfter = $env:UI_TESTS_START_AFTER_INDEX
    if ($ResumeAfterIndex -gt 0) {
        $env:UI_TESTS_START_AFTER_INDEX = "$ResumeAfterIndex"
        Write-Host "[resume] UI_TESTS_START_AFTER_INDEX=$ResumeAfterIndex (tier=$TierName)" -ForegroundColor Yellow
    } else {
        Remove-Item Env:UI_TESTS_START_AFTER_INDEX -ErrorAction SilentlyContinue
    }
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
        if ($null -ne $prevStartAfter) { $env:UI_TESTS_START_AFTER_INDEX = $prevStartAfter }
        else { Remove-Item Env:UI_TESTS_START_AFTER_INDEX -ErrorAction SilentlyContinue }
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
            Write-Host "Waiting for QEMU stack (timeout $($waitParams.TimeoutMinutes)m, interval $($waitParams.IntervalSec)s, maint-limit $($waitParams.MaxMaintenanceMinutes)m)..." -ForegroundColor Cyan
            $pre = Wait-KorusPlanPlaywrightStack -Root $Root -RunDir $RunDir @waitParams
        } else {
            $pre = Test-KorusPlanPlaywrightPreflight -Root $Root -RunDir $RunDir
            if (-not $pre.Ok) {
                Write-Host 'FAIL preflight:' -ForegroundColor Red
                $pre.Issues | ForEach-Object { Write-Host "  $_" }
                $analysis = Invoke-KorusPlanFailureAnalysis -Kind playwright -RunDir $RunDir -Root $Root `
                    -LastError ("preflight: " + ($pre.Issues -join "; "))
                if ($Tier -ne 'all-inner' -and $Tier -ne 'all-inner-core') {
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

if ($Tier -eq 'all-inner' -or $Tier -eq 'all-inner-core') {
    $seqKey = if ($Tier -eq 'all-inner-core') { 'all-inner-core' } else { 'all-inner' }
    $seq = @($manifest.tiers.$seqKey.sequential)
    $failed = @()
    foreach ($t in $seq) {
        if ($t -eq 'ui-tests') { $env:UI_TESTS_PROFILE = 'smoke' }
        if ($t -eq 'ui-ux-smoke') { $env:UI_TESTS_PROFILE = 'smoke' }
        if ($t -eq 'ui-ux-pr') { $env:UI_TESTS_PROFILE = 'pr' }
        if ($t -eq 'ui-ux-full') {
            $env:UI_TESTS_PROFILE = 'full'
            $env:UI_TESTS_UX_STRICT = '1'
            $env:UI_CONSOLE_GUARD = '1'
        }
        $def = $manifest.tiers.$t
        $ok = Invoke-TierPlaywright -TierName $t -TierDef $def -ResumeAfterIndex $(if ($t -eq $Tier -and $StartAfterTestIndex -gt 0) { $StartAfterTestIndex } else { 0 })
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
if ($Tier -eq 'ui-ux-full') {
    $env:UI_TESTS_PROFILE = 'full'
    $env:UI_TESTS_UX_STRICT = '1'
    $env:UI_CONSOLE_GUARD = '1'
}

$ok = Invoke-TierPlaywright -TierName $Tier -TierDef $tierDef -ResumeAfterIndex $StartAfterTestIndex
if ($ok -and $Tier -eq 'ui-ux-full') {
    $uxGate = Join-Path $Root "scripts\Assert-UiTestsUxFullGate.ps1"
    if (Test-Path $uxGate) {
        Write-Host ""
        Write-Host "=== ui-ux-full UX rubric gate ===" -ForegroundColor Cyan
        & $uxGate
        if ($LASTEXITCODE -ne 0) {
            $ok = $false
        }
    }
    $consoleGate = Join-Path $Root "scripts\Assert-UiTestsConsoleGate.ps1"
    if ($ok -and (Test-Path $consoleGate)) {
        Write-Host ""
        Write-Host "=== ui-ux-full browser console gate ===" -ForegroundColor Cyan
        & $consoleGate
        if ($LASTEXITCODE -ne 0) {
            $ok = $false
        }
    }
}
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
