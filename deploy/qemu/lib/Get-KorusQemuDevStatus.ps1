function Get-KorusQemuDevStatus {
    param([string]$RunDir, [string]$Root)
    . (Join-Path $PSScriptRoot "Test-KorusQemuProcess.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusQemuHostHealth.ps1")
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")
    . (Join-Path $PSScriptRoot "Test-KorusWebHotswap.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusGuestBootstrapPhase.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusQemuStackProfile.ps1")

    $stackProfile = Get-KorusQemuStackProfile
    $snapshotTick = $null
    $snapshotAt = $null
    $snapshotPath = Join-Path $RunDir "status-minute.snapshot.json"
    if (Test-Path $snapshotPath) {
        try {
            $snap = Get-Content $snapshotPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($snap.tick) { $snapshotTick = [int]$snap.tick }
            if ($snap.at) { $snapshotAt = [string]$snap.at }
        } catch {}
    }
    $loopPidPath = Join-Path $RunDir "status-minute-loop.pid"
    $minuteLoopRunning = $false
    if (Test-Path $loopPidPath) {
        $lp = (Get-Content $loopPidPath -Raw).Trim()
        if ($lp -match '^\d+$') {
            $minuteLoopRunning = [bool](Get-Process -Id ([int]$lp) -ErrorAction SilentlyContinue)
        }
    }
    $vmUp = Test-KorusQemuStackRunning -RunDir $RunDir
    $hotswap = $false
    $serverPhase = "n/a"
    $webPhase = "n/a"
    $webuiSync = $null

    $webuiTgz = Join-Path $RunDir "webui.tgz"
    if (Test-Path $webuiTgz) {
        $webuiSync = (Get-Item $webuiTgz).LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
    }

    $health = Get-KorusHostHealthSummary

    if ($vmUp) {
        $shk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
        if ($shk) {
            $st = Get-KorusGuestBootstrapTail -HostKey $shk -Port 12221
            $serverPhase = Get-KorusGuestBootstrapPhase -BootstrapText $st
        }
        $whk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
        if ($whk) {
            $wt = Get-KorusGuestBootstrapTail -HostKey $whk -Port 12222
            $webPhase = Get-KorusGuestBootstrapPhase -BootstrapText $wt
            $hotswap = Test-KorusGuestWebHotswapActive -HostKey $whk
        }
    }

    @{
        VmUp               = $vmUp
        StackProfile       = $stackProfile
        SnapshotTick       = $snapshotTick
        SnapshotAt         = $snapshotAt
        MinuteLoopRunning  = $minuteLoopRunning
        ApiHealth          = $health.ApiHealth
        ApiReady      = $health.ApiReady
        Web           = $health.Web
        TailwindCss   = (Test-KorusHostTailwindCss)
        Hotswap       = $hotswap
        ServerPhase   = $serverPhase
        WebPhase      = $webPhase
        WebuiSyncAt   = $webuiSync
        GoldenLock    = (Test-Path (Join-Path $RunDir "golden-path.no-auto-restart"))
        TcgForced     = ($env:KORUS_QEMU_FORCE_TCG -eq '1')
    }
}

function Write-KorusQemuDevStatus {
    param([hashtable]$S)
    Write-Host "=== Korus QEMU dev status ===" -ForegroundColor Cyan
    Write-Host "  Stack profile:  $($S.StackProfile)"
    Write-Host "  VM running:     $($S.VmUp)"
    Write-Host "  API /health:    $($S.ApiHealth)  /ready: $($S.ApiReady)"
    Write-Host "  UI :19088:      $($S.Web)  tailwind.css: $($S.TailwindCss)"
    Write-Host "  Hotswap:        $($S.Hotswap)"
    Write-Host "  Server phase:   $($S.ServerPhase)"
    Write-Host "  Web phase:      $($S.WebPhase)"
    if ($S.WebuiSyncAt) { Write-Host "  Last webui.tgz: $($S.WebuiSyncAt)" }
    if ($S.SnapshotTick) {
        $snapLine = "  Minute snapshot: tick=$($S.SnapshotTick)"
        if ($S.SnapshotAt) { $snapLine += " at $($S.SnapshotAt)" }
        if ($S.MinuteLoopRunning) {
            $snapLine += " (loop running)"
        } else {
            $snapLine += ' (loop stopped - stale chat-watch)'
            Write-Host $snapLine -ForegroundColor Yellow
            Write-Host "  -> refresh: .\scripts\qemu-status-minute.ps1 -Once" -ForegroundColor DarkGray
            Write-Host "  -> chat loop: .\scripts\start-qemu-status-loop.ps1 -Force" -ForegroundColor DarkGray
        }
        if ($S.MinuteLoopRunning) { Write-Host $snapLine -ForegroundColor DarkGray }
    }
    if ($S.GoldenLock) { Write-Host "  golden-path lock: active" -ForegroundColor Yellow }
    if ($S.TcgForced) { Write-Host "  KORUS_QEMU_FORCE_TCG=1" -ForegroundColor Yellow }
}
