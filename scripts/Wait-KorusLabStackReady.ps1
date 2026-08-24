#Requires -Version 5.1
# Unattended: QEMU up → guest core-api job → API /health + /ready (spec 030 stack prep).
param(
    [int]$MaxMinutes = 90,
    [int]$GuestPollMin = 3,
    [switch]$WarmIfDown,
    [switch]$LaunchRebuildIfNeeded,
    [switch]$RequireReady,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$LogPath = Join-Path $RunDir "lab-stack-wait.log"
$ApiUrl = "http://127.0.0.1:18080"
$WebUrl = "http://127.0.0.1:19088/"

if ($Help) {
    Write-Host @"
Usage: .\scripts\Wait-KorusLabStackReady.ps1 [-MaxMinutes 90] [-WarmIfDown] [-LaunchRebuildIfNeeded]

Blocks until API :18080 responds (and /ready if -RequireReady). Polls guest core-api-rebuild
every $GuestPollMin min; launches rebuild when API down and no job running (-LaunchRebuildIfNeeded).

Log: deploy/qemu/run/lab-stack-wait.log
"@
    exit 0
}

. (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")
. (Join-Path $PSScriptRoot "lib\Write-KorusCycleStatus.ps1")

function Write-WaitLog {
    param([string]$Message)
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    Add-Content -Path $LogPath -Value $line -Encoding UTF8
    Write-Host $line
}

function Test-KorusHttpCode {
    param([string]$Url)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $code = curl.exe -sS -m 12 -o NUL -w "%{http_code}" $Url 2>$null
    $ErrorActionPreference = $prev
    if (-not $code) { return "000" }
    return "$code".Trim()
}

function Get-KorusStackState {
    $core = (Test-KorusHttpCode "$ApiUrl/api/v1/health") -match '^2'
    $web = (Test-KorusHttpCode $WebUrl) -match '^2'
    $ready = $false
    if ($core) {
        try {
            $rd = Invoke-RestMethod -Uri "$ApiUrl/api/v1/health/ready" -TimeoutSec 12
            $ready = [bool]$rd.database_ok
        } catch {
            $ready = $false
        }
    }
    return @{ Core = $core; Web = $web; Ready = $ready }
}

function Test-GuestJobRunning {
    $jobScript = Join-Path $Root "scripts\qemu-guest-job.ps1"
    if (-not (Test-Path $jobScript)) { return $false }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $jobScript 2>&1 | Out-Null
    $running = ($LASTEXITCODE -eq 2)
    $ErrorActionPreference = $prev
    return $running
}

function Wait-GuestJobComplete {
    param([int]$TimeoutMin)
    $jobScript = Join-Path $Root "scripts\qemu-guest-job.ps1"
    Write-WaitLog "guest job poll start (max ${TimeoutMin}m, every ${GuestPollMin}m)"
    $deadline = (Get-Date).AddMinutes($TimeoutMin)
    $poll = 0
    while ((Get-Date) -lt $deadline) {
        $poll++
        Write-WaitLog "guest poll #$poll"
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $out = & $jobScript 2>&1 | Out-String
        $code = $LASTEXITCODE
        $ErrorActionPreference = $prev
        if ($out.Trim()) { Write-WaitLog $out.Trim() }
        if ($code -eq 0) {
            Write-WaitLog "guest job finished exit 0"
            return $true
        }
        if ($code -ne 2) {
            $st = Get-KorusStackState
            if ($st.Core) {
                Write-WaitLog "guest job exit $code but API healthy - continue"
                return $true
            }
            Write-WaitLog "guest job failed exit $code"
            return $false
        }
        Start-Sleep -Seconds ($GuestPollMin * 60)
    }
    Write-WaitLog "guest job timeout after ${TimeoutMin}m"
    return $false
}

function Wait-ApiHealth {
    param([int]$TimeoutMin)
    $deadline = (Get-Date).AddMinutes($TimeoutMin)
    while ((Get-Date) -lt $deadline) {
        $st = Get-KorusStackState
        $needReady = $RequireReady.IsPresent
        $ok = $st.Core -and $st.Web -and ((-not $needReady) -or $st.Ready)
        if ($ok) {
            Write-WaitLog "stack ready core=$($st.Core) web=$($st.Web) ready=$($st.Ready)"
            return $true
        }
        Write-WaitLog "waiting API/UI core=$($st.Core) web=$($st.Web) ready=$($st.Ready)"
        Start-Sleep -Seconds 30
    }
    return $false
}

Write-WaitLog "=== Wait-KorusLabStackReady start MaxMinutes=$MaxMinutes ==="
Write-KorusCycleStatus -Phase "stack:wait" -Detail "max ${MaxMinutes}m" -RunDir $RunDir

if ($WarmIfDown -and -not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Write-WaitLog "QEMU down - warm (KeepDisks)"
    Write-KorusCycleStatus -Phase "stack:warm" -RunDir $RunDir
    & (Join-Path $Root "scripts\qemu-dev-mode.ps1") -Mode warm
    if ($LASTEXITCODE -ne 0) {
        Write-KorusCycleStatus -Phase "stack:warm" -Status "failed" -Detail "qemu warm exit $LASTEXITCODE" -RunDir $RunDir
        exit 1
    }
}

$initial = Get-KorusStackState
if ($initial.Core -and $initial.Web -and ((-not $RequireReady) -or $initial.Ready)) {
    Write-WaitLog "stack already ready"
    Write-KorusCycleStatus -Phase "stack:ready" -Status "ok" -RunDir $RunDir
    exit 0
}

function Invoke-KorusGuestStackRemediate {
    Write-WaitLog "guest job failed or API stuck - remediate on server guest"
    Write-KorusCycleStatus -Phase "stack:remediate" -RunDir $RunDir
    . (Join-Path $Root "deploy\qemu\lib\Invoke-KorusGuestStackRemediate.ps1")
    return Invoke-KorusGuestStackRemediate -Root $Root -RunDir $RunDir -OnLog { param($m) Write-WaitLog $m }
}

$guestBudget = [math]::Max(15, $MaxMinutes - 10)
$guestOk = $true
if (Test-GuestJobRunning) {
    Write-WaitLog "core-api-rebuild already running"
    $guestOk = Wait-GuestJobComplete -TimeoutMin $guestBudget
} elseif ($LaunchRebuildIfNeeded) {
    $sshUp = (Test-NetConnection -ComputerName 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue).TcpTestSucceeded
    if ($sshUp) {
        Write-WaitLog "API down - launch qemu-sync-api-core -NoCache"
        Write-KorusCycleStatus -Phase "stack:api-rebuild" -RunDir $RunDir
        & (Join-Path $Root "scripts\qemu-sync-api-core.ps1") -NoCache
        if ($LASTEXITCODE -ne 0) { exit 1 }
        $guestOk = Wait-GuestJobComplete -TimeoutMin $guestBudget
    } else {
        Write-WaitLog "SSH :12221 not up - skip rebuild launch, wait health only"
    }
}

if (-not $guestOk) {
    Write-WaitLog "guest rebuild failed - auto remediate"
    $remediated = Invoke-KorusGuestStackRemediate
    if (-not $remediated) {
        $st = Get-KorusStackState
        if ($st.Core -and $st.Web) {
            Write-WaitLog "remediate skipped/failed but API/UI up - continue"
        } else {
            Write-KorusCycleStatus -Phase "stack:remediate" -Status "failed" -RunDir $RunDir
            exit 1
        }
    }
}

$healthBudget = [math]::Max(5, $MaxMinutes - $guestBudget)
if (-not (Wait-ApiHealth -TimeoutMin $healthBudget)) {
    Write-KorusCycleStatus -Phase "stack:wait" -Status "failed" -Detail "timeout" -RunDir $RunDir
    exit 1
}

Write-KorusCycleStatus -Phase "stack:ready" -Status "ok" -RunDir $RunDir
exit 0
