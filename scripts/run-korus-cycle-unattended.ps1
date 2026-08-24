#Requires -Version 5.1
# Full unattended cycle: buildIntegrity → lab stack → integrations → VPP until GREEN (resume checkpoint).
param(
    [ValidateSet('full', 'standard', 'quick')]
    [string]$VppLevel = 'full',
    [int]$MaxApiWaitMinutes = 90,
    [int]$MaxVppAttempts = 10,
    [switch]$SkipBuild,
    [switch]$SkipVpp,
    [switch]$RequireApiReady,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$EvDir = Join-Path $RunDir 'vpp-evidence'
$LogPath = Join-Path $RunDir 'cycle-unattended.log'
$GreenPath = Join-Path $EvDir 'vpp-green.json'
$CheckpointPath = Join-Path $EvDir 'vpp-checkpoint.json'

if ($Help) {
    Write-Host @"
Usage: .\scripts\run-korus-cycle-unattended.ps1 [-VppLevel full] [-MaxApiWaitMinutes 90]

Unattended master cycle (no manual polling):
  1. buildIntegrity (host)
  2. Wait-KorusLabStackReady (QEMU warm, guest rebuild poll, API health)
  3. Wait-IntegrationsOnline
  4. VPP until GREEN (resume vpp-checkpoint.json if present)

Background: .\scripts\Start-KorusCycleUnattended.ps1
Status: deploy/qemu/run/cycle-unattended-status.json
Log: deploy/qemu/run/cycle-unattended.log
"@
    exit 0
}

. (Join-Path $PSScriptRoot 'lib\Write-KorusCycleStatus.ps1')
$lockScript = Join-Path $Root 'scripts\vpp\Invoke-VppLabRunLock.ps1'

function Write-CycleLog {
    param([string]$Message)
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    Add-Content -Path $LogPath -Value $line -Encoding UTF8
    Write-Host $line
}

if (Test-Path $lockScript) {
    & $lockScript -Action Enter -TaskName 'korus-cycle-unattended'
    if ($LASTEXITCODE -eq 2) { exit 2 }
}

$code = 1
try {
    Write-CycleLog '=== run-korus-cycle-unattended START ==='
    Write-KorusCycleStatus -Phase 'init' -RunDir $RunDir

    if (-not $SkipBuild) {
        Write-KorusCycleStatus -Phase 'build:buildIntegrity' -RunDir $RunDir
        Write-CycleLog 'buildIntegrity...'
        Push-Location $Root
        try {
            $prevEap = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & (Join-Path $Root 'gradlew.bat') buildIntegrity 2>&1 | Tee-Object -FilePath (Join-Path $RunDir 'cycle-build.log') -Append
            $biExit = $LASTEXITCODE
            $ErrorActionPreference = $prevEap
            if ($biExit -ne 0) { throw "buildIntegrity exit $biExit" }
        } finally {
            Pop-Location
        }
        Write-CycleLog 'buildIntegrity PASS'
    }

    $waitArgs = @{
        MaxMinutes              = $MaxApiWaitMinutes
        WarmIfDown              = $true
        LaunchRebuildIfNeeded   = $true
    }
    if ($RequireApiReady) { $waitArgs['RequireReady'] = $true }
    & (Join-Path $Root 'scripts\Wait-KorusLabStackReady.ps1') @waitArgs
    if ($LASTEXITCODE -ne 0) { throw 'lab stack not ready' }

    Write-KorusCycleStatus -Phase 'stack:integrations' -RunDir $RunDir
    Write-CycleLog 'Wait-IntegrationsOnline...'
    $env:KORUS_QEMU_THREE_VM = '1'
    & (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') -MaxSec 1800 -StartVmIfDown -RepairGateway
    if ($LASTEXITCODE -ne 0) { throw 'integrations not ready' }

    if ($SkipVpp) {
        Write-CycleLog 'SkipVpp — done after stack'
        Write-KorusCycleStatus -Phase 'done' -Status 'ok' -Detail 'skip-vpp' -RunDir $RunDir
        $code = 0
        return
    }

    if (Test-Path $GreenPath) {
        try {
            $g = Get-Content -Raw $GreenPath | ConvertFrom-Json
            if ($g.status -eq 'GREEN' -and $g.full_coverage -eq $true) {
                Write-CycleLog 'vpp-green.json already GREEN + full_coverage'
                Write-KorusCycleStatus -Phase 'vpp:green' -Status 'ok' -RunDir $RunDir
                $code = 0
                return
            }
        } catch { }
    }

    Write-KorusCycleStatus -Phase 'vpp:run' -RunDir $RunDir
    $env:KORUS_QEMU_THREE_VM = '1'
    $env:VPP_RETRY_DELAY_SEC = if ($env:VPP_RETRY_DELAY_SEC) { $env:VPP_RETRY_DELAY_SEC } else { '120' }
    $env:VPP_CHAT_REPORT_SEC = if ($env:VPP_CHAT_REPORT_SEC) { $env:VPP_CHAT_REPORT_SEC } else { '300' }

    if (Test-Path $CheckpointPath) {
        Write-CycleLog "VPP resume from checkpoint"
        & (Join-Path $Root 'scripts\vpp\Resume-VppMonitoredLabRun.ps1') -SkipStackPrep -MaxAttempts $MaxVppAttempts
    } else {
        Write-CycleLog "VPP fresh monitored run level=$VppLevel"
        & (Join-Path $Root 'scripts\vpp\Start-VppMonitoredLabRun.ps1') -Level $VppLevel -MaxAttempts $MaxVppAttempts -SkipStackPrep -NoStop
    }
    $code = $LASTEXITCODE

    if ($code -eq 0 -and (Test-Path $GreenPath)) {
        Write-KorusCycleStatus -Phase 'vpp:green' -Status 'ok' -RunDir $RunDir
        Write-CycleLog 'VPP GREEN'
    } else {
        Write-KorusCycleStatus -Phase 'vpp:run' -Status 'failed' -Detail "exit $code" -RunDir $RunDir
        Write-CycleLog "VPP finished exit $code (checkpoint preserved for resume)"
    }
} catch {
    Write-CycleLog "[FAIL] $($_.Exception.Message)"
    Write-KorusCycleStatus -Phase 'failed' -Status 'failed' -Detail $_.Exception.Message -RunDir $RunDir
    $code = 1
} finally {
    if (Test-Path $lockScript) { & $lockScript -Action Exit -Force | Out-Null }
}
exit $code
