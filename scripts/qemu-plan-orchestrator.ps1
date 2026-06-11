# Golden-path orchestrator: QEMU up -> stack ready -> smoke -> Playwright -> gate report.
# Minute status + auto-remediate via qemu-status-minute; chat ticks for Cursor agent.
param(
    [int]$IntervalSeconds = 60,
    [int]$MaxWaitMinutes = 90,
    [int]$MaxAcceptanceMinutes = 120,
    [switch]$SkipVmUp,
    [switch]$Help
)

$ErrorActionPreference = "Continue"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-plan-orchestrator.ps1 [-IntervalSeconds 60] [-MaxWaitMinutes 90] [-MaxAcceptanceMinutes 120] [-SkipVmUp]

Runs full acceptance plan with per-minute chat output and auto-remediate.
Stop: Ctrl+C or .\scripts\stop-qemu-plan-orchestrator.ps1
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
. (Join-Path $Root "deploy\qemu\lib\Start-KorusQemuGuestRedeploy.ps1")
$StatePath = Join-Path $RunDir "plan-orchestrator.json"
$LogPath = Join-Path $RunDir "plan-orchestrator.log"
$PidPath = Join-Path $RunDir "plan-orchestrator.pid"
$MinuteScript = Join-Path $Root "scripts\qemu-status-minute.ps1"

. (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")

function Log([string]$Line) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $full = "$ts $Line"
    Add-Content -Path $LogPath -Value $full -Encoding utf8
}

function Get-PlanState {
    $def = @{
        phase             = 'waiting_stack'
        startedAt         = (Get-Date).ToString('o')
        smokeOk           = $false
        playwrightOk      = $false
        reportOk          = $false
        lastError         = ''
        smokeRunning      = $false
        playwrightRunning = $false
        reportRunning     = $false
    }
    if (-not (Test-Path $StatePath)) { return $def }
    try {
        $o = Get-Content $StatePath -Raw | ConvertFrom-Json
        return @{
            phase          = [string]$o.phase
            startedAt      = [string]$o.startedAt
            smokeOk        = [bool]$o.smokeOk
            playwrightOk   = [bool]$o.playwrightOk
            reportOk       = [bool]$o.reportOk
            lastError      = [string]$o.lastError
            smokeRunning      = [bool]$o.smokeRunning
            playwrightRunning = [bool]$o.playwrightRunning
            reportRunning     = [bool]$o.reportRunning
        }
    } catch {
        return $def
    }
}

function Set-PlanState([hashtable]$State) {
    $State | ConvertTo-Json -Compress | Set-Content -Path $StatePath -Encoding utf8 -NoNewline
}

function Emit-PlanChatTick {
    param([string]$Phase, [string]$SummaryRu, [string]$Detail = '')
    $payload = @{
        prompt = "QEMU plan orchestrator phase=$Phase. Post summaryRu in Russian to chat. $SummaryRu $Detail No qemu-down. Do not kill non-Korus qemu."
        phase = $Phase
        summaryRu = $SummaryRu
        detail = $Detail
        stateFile = 'deploy/qemu/run/plan-orchestrator.json'
        snapshot = 'deploy/qemu/run/status-minute.snapshot.json'
    }
    $json = ($payload | ConvertTo-Json -Compress -Depth 5)
    Write-Output "AGENT_LOOP_TICK_qemu_plan $json"
}

function Test-KorusStackReady {
    param([hashtable]$Snap)
    if ($Snap -and $Snap.stackReady) { return $true }
    try {
        $h = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/v1/health' -UseBasicParsing -TimeoutSec 10
        if ($h.StatusCode -ne 200) { return $false }
        $w = Invoke-WebRequest -Uri 'http://127.0.0.1:19088/' -UseBasicParsing -TimeoutSec 10
        if ($w.StatusCode -ne 200) { return $false }
        $rd = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health/ready' -TimeoutSec 10
        return [bool]$rd.database_ok
    } catch {
        return $false
    }
}

function Invoke-PlanSmoke {
    . (Join-Path $Root "deploy\qemu\lib\Get-KorusLanHostIp.ps1")
    $lan = Write-KorusQemuLanHostInfo -RunDir $RunDir
    Log "phase smoke ExpectWsHost=$lan"
    & (Join-Path $Root "scripts\smoke-korus-web.ps1") -WebBaseUrl 'http://127.0.0.1:19088' -CheckApi -ExpectWsHost $lan
    return ($LASTEXITCODE -eq 0)
}

function Get-PlanSmokeFailureKind {
    if (-not (Test-KorusStackReady -Snap $null)) { return 'stack_down' }
    . (Join-Path $Root "deploy\qemu\lib\Get-KorusLanHostIp.ps1")
    Write-KorusQemuLanHostInfo -RunDir $RunDir | Out-Null
    if (Test-KorusWebClientWsHostMismatch -RunDir $RunDir) { return 'ws_url' }
    return 'other'
}

function Invoke-PlanPlaywright {
    Log "phase playwright"
    $e2e = Join-Path $Root "tests\e2e-web"
    Push-Location $e2e
    try {
        if (-not (Test-Path node_modules)) {
            npm ci 2>&1 | ForEach-Object { Log "  npm: $_" }
            if ($LASTEXITCODE -ne 0) { return $false }
        }
        $env:KORUS_WEB_URL = 'http://127.0.0.1:19088'
        $env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
        $env:KORUS_API_URL = 'http://127.0.0.1:18080'
        $pwLog = Join-Path $RunDir "playwright-orchestrator.log"
        npx playwright test 2>&1 | Tee-Object -FilePath $pwLog | ForEach-Object { Log "  pw: $_" }
        return ($LASTEXITCODE -eq 0)
    } finally {
        Pop-Location
    }
}

function Invoke-PlanGateReport {
    Log "phase gate-report"
    & (Join-Path $Root "scripts\write-runtime-gate-report.ps1") `
        -WebBaseUrl 'http://127.0.0.1:19088' -ApiBaseUrl 'http://127.0.0.1:18080'
    return ($LASTEXITCODE -eq 0)
}

if (-not (Test-Path $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }
Set-Content -Path $PidPath -Value $PID -Encoding ascii -NoNewline
Log "orchestrator start PID=$PID"

$state = Get-PlanState
if ($state.phase -eq 'completed') {
    $state.phase = 'waiting_stack'
    $state.smokeOk = $false
    $state.playwrightOk = $false
    $state.reportOk = $false
}
Set-PlanState $state

if (-not $SkipVmUp) {
    $stackRunning = Test-KorusQemuStackRunning -RunDir $RunDir
    if (-not $stackRunning) {
        Log "qemu-up -KeepDisks"
        Write-Output "--- PLAN: starting Korus QEMU (KeepDisks) ---"
        & (Join-Path $Root "scripts\qemu-up.ps1") -KeepDisks 2>&1 | ForEach-Object { Log "  up: $_" }
        Emit-PlanChatTick -Phase 'starting_vms' -SummaryRu 'qemu-up -KeepDisks started, waiting cloud-init/bootstrap.'
    }
}

$stackDeadline = (Get-Date).AddMinutes($MaxWaitMinutes)
$acceptanceDeadline = (Get-Date).AddMinutes($MaxAcceptanceMinutes)
$first = $true

while ($true) {
    if (-not $first) { Start-Sleep -Seconds $IntervalSeconds }
    $first = $false

    $stackReadyNow = Test-KorusStackReady -Snap $null
    if ((Get-Date) -gt $stackDeadline -and $state.phase -eq 'waiting_stack' -and -not $stackReadyNow) {
        $state.phase = 'failed'
        $state.lastError = "timeout ${MaxWaitMinutes}m waiting stack"
        Set-PlanState $state
        Log "FAILED stack wait timeout"
        Emit-PlanChatTick -Phase 'failed' -SummaryRu "Timeout ${MaxWaitMinutes}m stack not ready." -Detail 'Check status-remediate.log bootstrap.'
        exit 1
    }
    if ((Get-Date) -gt $acceptanceDeadline -and $state.phase -in @('running_smoke', 'running_playwright', 'writing_report', 'remediating_web')) {
        $state.phase = 'failed'
        $state.lastError = "timeout ${MaxAcceptanceMinutes}m acceptance phase"
        Set-PlanState $state
        Log "FAILED acceptance timeout phase=$($state.phase)"
        Emit-PlanChatTick -Phase 'failed' -SummaryRu "Timeout ${MaxAcceptanceMinutes}m acceptance (smoke/Playwright/report)." -Detail $state.lastError
        exit 1
    }

    # Minute status + auto-remediate
    try {
        & $MinuteScript -Once 2>&1 | ForEach-Object { Write-Output $_ }
    } catch {
        Log "status-minute error: $_"
    }

    $snap = $null
    $snapPath = Join-Path $RunDir "status-minute.snapshot.json"
    if (Test-Path $snapPath) {
        $snap = Get-Content $snapPath -Raw | ConvertFrom-Json
    }

    $summaryRu = if ($snap) { [string]$snap.summaryRu } else { 'status-minute unavailable' }
    $remediate = if ($snap) { [string]$snap.remediate } else { '' }
    $phase = $state.phase

    Write-Output "--- PLAN CHAT (ru) phase=$phase ---"
    Write-Output $summaryRu
    if ($remediate) { Write-Output "remediate: $remediate" }

    Emit-PlanChatTick -Phase $phase -SummaryRu $summaryRu -Detail $(if ($remediate) { "remediate: $remediate" } else { '' })

    switch ($state.phase) {
        'waiting_stack' {
            if (Test-KorusStackReady -Snap @{ stackReady = [bool]$snap.stackReady }) {
                Log "stack ready -> smoke"
                $state.phase = 'running_smoke'
                $state.smokeRunning = $false
                Set-PlanState $state
                Write-Output "--- PLAN: stack ready, next smoke ---"
                Emit-PlanChatTick -Phase 'running_smoke' -SummaryRu 'Stack ready. Running smoke-korus-web.'
            }
        }
        'running_smoke' {
            if (-not $state.smokeRunning) {
                $state.smokeRunning = $true
                Set-PlanState $state
                $ok = Invoke-PlanSmoke
                $state.smokeOk = $ok
                $state.smokeRunning = $false
                if ($ok) {
                    $state.phase = 'running_playwright'
                    $state.playwrightRunning = $false
                    $state.lastError = ''
                    Log "smoke OK"
                    Write-Output "--- PLAN: smoke OK ---"
                    Emit-PlanChatTick -Phase 'running_playwright' -SummaryRu 'Smoke passed. Running Playwright.'
                } else {
                    $failKind = Get-PlanSmokeFailureKind
                    $state.lastError = "smoke failed ($failKind)"
                    if ($failKind -eq 'ws_url') {
                        Log "smoke FAIL wsUrl -> web redeploy, retry smoke"
                        Write-Output "--- PLAN: smoke FAIL wsUrl, redeploy web ---"
                        Start-KorusQemuGuestRedeploy -Role web -RunDir $RunDir -Root $Root -Reason 'smoke wsUrl mismatch' | Out-Null
                        $state.phase = 'remediating_web'
                    } elseif ($failKind -eq 'stack_down') {
                        $state.phase = 'waiting_stack'
                        Log "smoke FAIL stack down -> waiting_stack"
                        Write-Output "--- PLAN: smoke FAIL, stack down ---"
                    } else {
                        Log "smoke FAIL other -> retry smoke (stack ready)"
                        Write-Output "--- PLAN: smoke FAIL (stack ready), retry next tick ---"
                        $state.phase = 'running_smoke'
                    }
                }
                Set-PlanState $state
            }
        }
        'remediating_web' {
            . (Join-Path $Root "deploy\qemu\lib\Get-KorusLanHostIp.ps1")
            if (Test-KorusStackReady -Snap @{ stackReady = [bool]$snap.stackReady }) {
                if (-not (Test-KorusWebClientWsHostMismatch -RunDir $RunDir)) {
                    Log "web wsUrl fixed -> smoke"
                    $state.phase = 'running_smoke'
                    $state.smokeRunning = $false
                    $state.lastError = ''
                    Set-PlanState $state
                    Write-Output "--- PLAN: web wsUrl OK, retry smoke ---"
                    Emit-PlanChatTick -Phase 'running_smoke' -SummaryRu 'Web redeploy done, wsUrl OK. Retry smoke.'
                } else {
                    Write-Output "--- PLAN: waiting web redeploy (wsUrl still wrong) ---"
                }
            }
        }
        'running_playwright' {
            if (-not $state.playwrightRunning) {
                $state.playwrightRunning = $true
                Set-PlanState $state
                $ok = Invoke-PlanPlaywright
                $state.playwrightOk = $ok
                $state.playwrightRunning = $false
                if ($ok) {
                    $state.phase = 'writing_report'
                    $state.lastError = ''
                    Log "playwright OK"
                } else {
                    $state.lastError = 'playwright failed'
                    $state.phase = 'failed'
                    Log "playwright FAIL"
                    Emit-PlanChatTick -Phase 'failed' -SummaryRu 'Playwright failed. See playwright-orchestrator.log'
                }
                Set-PlanState $state
            }
        }
        'writing_report' {
            if ($state.reportRunning) { break }
            $state.reportRunning = $true
            Set-PlanState $state
            $ok = Invoke-PlanGateReport
            $state.reportOk = $ok
            $state.reportRunning = $false
            if ($ok) {
                $state.phase = 'completed'
                $state.lastError = ''
                Log "COMPLETED"
                Write-Output "--- PLAN: COMPLETED ---"
                Emit-PlanChatTick -Phase 'completed' -SummaryRu 'Plan done: stack smoke Playwright runtime-gate-report.'
            } else {
                $state.lastError = 'gate report failed'
                $state.phase = 'failed'
                Log "gate report FAIL"
            }
            Set-PlanState $state
        }
        'completed' {
            Write-Output "--- PLAN: already completed ---"
            Emit-PlanChatTick -Phase 'completed' -SummaryRu 'Plan already completed successfully.'
            exit 0
        }
        'failed' {
            if (Test-KorusStackReady -Snap @{ stackReady = [bool]$snap.stackReady }) {
                Emit-PlanChatTick -Phase 'failed' -SummaryRu "Plan failed: $($state.lastError). Stack ready, resume acceptance."
                if ($state.lastError -match 'smoke|wsUrl|ws_url') {
                    $state.phase = 'running_smoke'
                } elseif ($state.lastError -match 'playwright') {
                    $state.phase = 'running_playwright'
                } else {
                    $state.phase = 'writing_report'
                }
                $state.smokeRunning = $false
                $state.playwrightRunning = $false
                $state.reportRunning = $false
            } else {
                Emit-PlanChatTick -Phase 'failed' -SummaryRu "Plan failed: $($state.lastError). Auto-remediate continues."
                $state.phase = 'waiting_stack'
            }
            Set-PlanState $state
        }
    }

    if ($state.phase -eq 'completed') { exit 0 }
}
