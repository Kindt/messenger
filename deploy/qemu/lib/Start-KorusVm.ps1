function Start-KorusQemuVm {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web")]
        [string]$Role
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Resolve-Qemu.ps1")
    . (Join-Path $PSScriptRoot "Get-AlpineImage.ps1")
    . (Join-Path $PSScriptRoot "New-CidataIso.ps1")
    . (Join-Path $PSScriptRoot "Test-KorusWhpx.ps1")

    $qemu = Resolve-KorusQemu
    if (-not $qemu) { throw "QEMU not found. Run deploy/qemu/install-qemu.ps1" }

    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    $base = Get-KorusCloudImage
    $disk = New-KorusVmOverlayDisk -Name $Role -BaseImage $base
    $seed = New-KorusCidataSeed -Role $Role
    $serialName = "$Role-serial.log"
    $serialLog = Join-Path $KorusQemuRunDir $serialName
    $pidFile = Join-Path $KorusQemuRunDir "$Role.pid"

    $mac = if ($Role -eq "server") { "52:54:00:12:34:10" } else { "52:54:00:12:34:20" }
    $name = "korus-$Role"

    $hostFwd = if ($Role -eq "server") {
        @(
            "hostfwd=tcp:0.0.0.0:18080-:8080",
            "hostfwd=tcp:0.0.0.0:18082-:8082",
            "hostfwd=tcp:0.0.0.0:18081-:8081",
            "hostfwd=tcp:0.0.0.0:12221-:22"
        ) -join ","
    } else {
        "hostfwd=tcp:0.0.0.0:19088-:9088,hostfwd=tcp:0.0.0.0:12222-:22"
    }

    $whpx = Test-KorusWhpxAvailable
    if ($whpx.Ok) {
        # host/max often crash WHPX on Windows ("Unexpected VP exit code 4"); qemu64 is stable
        $accelArgs = @("-accel", "whpx", "-cpu", "qemu64")
    } else {
        $accelArgs = @("-accel", "tcg")
        Write-Warning "$($whpx.Message). Run: .\scripts\qemu-fast-up.ps1 (elevated) for WHPX."
    }

    $seedPath = $seed.Path -replace '\\', '/'
    # if=none without -device left the seed invisible; ide CD is what cloud-init NoCloud expects
    $seedDrive = @(
        "-drive", "file=$seedPath,format=raw,if=ide,media=cdrom,readonly=on"
    )
    $memMb = if ($Role -eq "server") { $KorusQemuServerMemoryMb } else { $KorusQemuWebMemoryMb }
    $smp = if ($Role -eq "server") { $KorusQemuServerSmp } else { $KorusQemuWebSmp }
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
        "-serial", "file:$serialName",
        "-display", "none"
    )

    $errLog = Join-Path $KorusQemuRunDir "$Role-qemu.err"
    if (Test-Path $errLog) { Remove-Item -Force $errLog }

    Write-Host "Starting $name ($($whpx.Mode), seed $($seed.Type)) ..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath $qemu -ArgumentList $args -WorkingDirectory $KorusQemuRunDir `
        -PassThru -WindowStyle Hidden -RedirectStandardError $errLog
    Start-Sleep -Seconds 4
    if ($proc.HasExited) {
        $err = if (Test-Path $errLog) { Get-Content -Raw $errLog } else { "" }
        throw "QEMU $name exited immediately. $err"
    }
    $proc.Id | Set-Content -Path $pidFile -Encoding ascii
    Write-Host "  PID $($proc.Id)  accel=$($whpx.Mode)  serial: $serialLog" -ForegroundColor DarkGray
    return $proc.Id
}
