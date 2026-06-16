function Start-KorusQemuVm {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web", "integrations")]
        [string]$Role
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Resolve-Qemu.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusCloudImage.ps1")
    . (Join-Path $PSScriptRoot "New-CidataIso.ps1")
    . (Join-Path $PSScriptRoot "Test-KorusWhpx.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusQemuDisplayMode.ps1")
    . (Join-Path $PSScriptRoot "Write-KorusDebugLog.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusQemuStackProfile.ps1")

    $stackProfile = Get-KorusQemuStackProfile
    Write-KorusDebugLog -Location "Start-KorusVm.ps1:entry" -Message "vm start" -HypothesisId "H1" -Data @{
        Role = $Role; displayEnv = $env:KORUS_QEMU_DISPLAY; stackProfile = $stackProfile
    }

    $qemu = Resolve-KorusQemu
    if (-not $qemu) { throw "QEMU not found. Run deploy/qemu/install-qemu.ps1" }

    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    Initialize-KorusLegacyVmDisks
    $base = Get-KorusCloudImage
    $diskName = Get-KorusVmDiskName -Role $Role -StackProfile $stackProfile
    $disk = New-KorusVmOverlayDisk -Name $diskName -BaseImage $base
    $seed = New-KorusCidataSeed -Role $Role
    $serialName = "$Role-serial.log"
    $serialLog = Join-Path $KorusQemuRunDir $serialName
    $pidFile = Join-Path $KorusQemuRunDir "$Role.pid"

    $mac = switch ($Role) {
        "server" { "52:54:00:12:34:10" }
        "web" { "52:54:00:12:34:20" }
        "integrations" { "52:54:00:12:34:30" }
    }
    $name = "korus-$Role"

    $hostFwd = switch ($Role) {
        "server" {
            @(
                "hostfwd=tcp:0.0.0.0:18080-:8080",
                "hostfwd=tcp:0.0.0.0:18082-:8082",
                "hostfwd=tcp:0.0.0.0:18081-:8081",
                "hostfwd=tcp:0.0.0.0:17880-:7880",
                "hostfwd=tcp:0.0.0.0:17881-:7881",
                "hostfwd=udp:0.0.0.0:17882-:7882",
                "hostfwd=tcp:0.0.0.0:12221-:22"
            ) -join ","
        }
        "web" {
            "hostfwd=tcp:0.0.0.0:19088-:9088,hostfwd=tcp:0.0.0.0:3478-:3478,hostfwd=udp:0.0.0.0:3478-:3478,hostfwd=tcp:0.0.0.0:12222-:22"
        }
        "integrations" {
            @(
                "hostfwd=tcp:0.0.0.0:18190-:8090",
                "hostfwd=tcp:0.0.0.0:18091-:8091",
                "hostfwd=tcp:0.0.0.0:18088-:8088",
                "hostfwd=tcp:0.0.0.0:18089-:8089",
                "hostfwd=tcp:0.0.0.0:18092-:8092",
                "hostfwd=tcp:0.0.0.0:18093-:8093",
                "hostfwd=tcp:0.0.0.0:18094-:8094",
                "hostfwd=tcp:0.0.0.0:18095-:8095",
                "hostfwd=tcp:0.0.0.0:18096-:8096",
                "hostfwd=tcp:0.0.0.0:18097-:8097",
                "hostfwd=tcp:0.0.0.0:18087-:8080",
                "hostfwd=tcp:0.0.0.0:12223-:22"
            ) -join ","
        }
    }

    $whpx = Test-KorusWhpxAvailable
    if ($env:KORUS_QEMU_FORCE_TCG -eq "1") {
        $accelArgs = @("-accel", "tcg")
        $whpx = @{ Ok = $false; Mode = "tcg"; Message = "KORUS_QEMU_FORCE_TCG=1" }
    } elseif ($whpx.Ok) {
        # host/max often crash WHPX on Windows ("Unexpected VP exit code 4"); qemu64 is stable.
        # kernel-irqchip=off avoids common WHPX instability on Windows 10/11.
        $whpxAccel = if ($env:KORUS_QEMU_WHPX_KERNEL_IRQCHIP -eq "on") { "whpx" } else { "whpx,kernel-irqchip=off" }
        $accelArgs = @("-accel", $whpxAccel, "-cpu", "qemu64")
    } else {
        $accelArgs = @("-accel", "tcg")
        Write-Warning "$($whpx.Message). Run: .\scripts\qemu-fast-up.ps1 (elevated) for WHPX."
    }

    $seedPath = $seed.Path -replace '\\', '/'
    # if=none without -device left the seed invisible; ide CD is what cloud-init NoCloud expects
    $seedDrive = @(
        "-drive", "file=$seedPath,format=raw,if=ide,media=cdrom,readonly=on"
    )
    $memMb = switch ($Role) {
        "server" { $KorusQemuServerMemoryMb }
        "web" { $KorusQemuWebMemoryMb }
        "integrations" { $KorusQemuIntegrationsMemoryMb }
    }
    $smp = switch ($Role) {
        "server" { $KorusQemuServerSmp }
        "web" { $KorusQemuWebSmp }
        "integrations" { $KorusQemuIntegrationsSmp }
    }
    $errLog = Join-Path $KorusQemuRunDir "$Role-qemu.err"
    if (Test-Path $errLog) { Remove-Item -Force $errLog -ErrorAction SilentlyContinue }

    $displayModes = @((Get-KorusQemuDisplayArgs -Role $Role).Mode)
    $primary = $displayModes[0]
    if ($primary -eq "gtk") { $displayModes += @("sdl", "default") }

    $proc = $null
    $display = $null
    foreach ($tryMode in ($displayModes | Select-Object -Unique)) {
        if (Test-Path $errLog) { Remove-Item -Force $errLog -ErrorAction SilentlyContinue }
        $display = Get-KorusQemuDisplayArgs -Role $Role -ModeOverride $tryMode
        $args = @(
            "-name", $name,
            "-machine", "pc",
            "-m", "$memMb",
            "-smp", "$smp"
        ) + $accelArgs + @(
            "-drive", "file=$($disk -replace '\\','/'),if=virtio,format=qcow2"
        ) + $seedDrive + @(
            "-device", "virtio-rng-pci",
            "-netdev", "user,id=net0,$hostFwd",
            "-device", "virtio-net-pci,netdev=net0,mac=$mac",
            "-serial", "file:$serialName"
        ) + $display.Args

        $modeHint = if ($display.Graphical) { "display=$($display.Mode)" } else { "headless" }
        if ($tryMode -ne $primary) {
            Write-Warning "Retrying $name with display=$($display.Mode) (previous mode failed)"
        } else {
            Write-Host "Starting $name ($($whpx.Mode), $modeHint, seed $($seed.Type)) ..." -ForegroundColor Cyan
        }
        $proc = Start-Process -FilePath $qemu -ArgumentList $args -WorkingDirectory $KorusQemuRunDir `
            -PassThru -WindowStyle $display.WindowStyle -RedirectStandardError $errLog
        Start-Sleep -Seconds 4
        $exited = $proc.HasExited
        $errTail = if (Test-Path $errLog) { (Get-Content $errLog -Tail 3 -ErrorAction SilentlyContinue) -join " | " } else { "" }
        Write-KorusDebugLog -Location "Start-KorusVm.ps1:display-try" -Message "display attempt" -HypothesisId "H1" -Data @{
            Role = $Role; tryMode = $tryMode; pid = $proc.Id; exited = $exited; errTail = $errTail
        }
        if (-not $exited) { break }
        $proc = $null
    }
    if (-not $proc -or $proc.HasExited) {
        $err = if (Test-Path $errLog) { Get-Content -Raw $errLog } else { "" }
        Write-KorusDebugLog -Location "Start-KorusVm.ps1:fail" -Message "vm exit" -HypothesisId "H1" -Data @{ Role = $Role; err = $err.Substring(0, [Math]::Min(500, $err.Length)) }
        throw "QEMU $name exited immediately. $err"
    }
    Write-KorusDebugLog -Location "Start-KorusVm.ps1:ok" -Message "vm running" -HypothesisId "H1" -Data @{
        Role = $Role; pid = $proc.Id; display = $display.Mode; accel = $whpx.Mode
    }
    $proc.Id | Set-Content -Path $pidFile -Encoding ascii
    Write-Host "  PID $($proc.Id)  accel=$($whpx.Mode)  display=$($display.Mode)  serial: $serialLog" -ForegroundColor DarkGray
    if ($display.Graphical) {
        Write-Host "  GTK window: bootstrap/Docker log on tty1 (not login); Ctrl+Alt+G grab" -ForegroundColor DarkGray
    }
    return $proc.Id
}
