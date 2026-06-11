function Test-KorusWhpxAvailable {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Resolve-Qemu.ps1")

    if ($script:KorusWhpxProbeCache -and $script:KorusWhpxProbeCacheTime) {
        $age = ((Get-Date) - $script:KorusWhpxProbeCacheTime).TotalSeconds
        if ($age -lt 300) {
            return $script:KorusWhpxProbeCache
        }
    }

    $qemu = Resolve-KorusQemu
    if (-not $qemu) {
        return [PSCustomObject]@{
            Ok      = $false
            Mode    = "tcg"
            Message = "QEMU not found"
        }
    }

    $errFile = Join-Path $KorusQemuRunDir "whpx-probe.err"
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    if (Test-Path $errFile) { Remove-Item -Force $errFile }

    $proc = Start-Process -FilePath $qemu -ArgumentList @(
        "-name", "korus-whpx-probe",
        "-accel", "whpx", "-machine", "none", "-display", "none", "-serial", "null"
    ) -PassThru -WindowStyle Hidden -RedirectStandardError $errFile

    Start-Sleep -Seconds 2
    $stderr = if (Test-Path $errFile) { Get-Content -Raw $errFile } else { "" }
    $failed = $stderr -match "failed to initialize whpx|No accelerator found|WHPX: No accelerator"

    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
    Get-Process -Name "qemu-system-x86_64" -ErrorAction SilentlyContinue |
        Where-Object { $_.Id -ne $proc.Id } |
        ForEach-Object {
            $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine
            if ($cmd -match "korus-whpx-probe") {
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            }
        }

    if (-not $failed -and $stderr -notmatch "failed to initialize whpx") {
        $script:KorusWhpxProbeCache = [PSCustomObject]@{
            Ok      = $true
            Mode    = "whpx"
            Message = "WHPX accelerator available"
        }
        $script:KorusWhpxProbeCacheTime = Get-Date
        return $script:KorusWhpxProbeCache
    }
    $script:KorusWhpxProbeCache = [PSCustomObject]@{
        Ok      = $false
        Mode    = "tcg"
        Message = "WHPX not available (will use TCG)"
    }
    $script:KorusWhpxProbeCacheTime = Get-Date
    return $script:KorusWhpxProbeCache
}
