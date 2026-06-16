. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\Test-KorusQemuProcess.ps1")

foreach ($role in @("server", "web", "integrations")) {
    $pidFile = Join-Path $KorusQemuRunDir "$role.pid"
    if (Test-Path $pidFile) {
        $procId = (Get-Content $pidFile -Raw).Trim()
        if ($procId -match '^\d+$') {
            Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped korus-$role (PID $procId)"
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | ForEach-Object {
    if (-not (Test-KorusQemuProcess -ProcessId $_.Id)) { return }
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -match "korus-server") { Write-Host "Stopped orphan korus-server (PID $($_.Id))" }
    elseif ($cmd -match "korus-web") { Write-Host "Stopped orphan korus-web (PID $($_.Id))" }
    elseif ($cmd -match "korus-integrations") { Write-Host "Stopped orphan korus-integrations (PID $($_.Id))" }
    elseif ($cmd -match "korus-whpx-probe") { Write-Host "Stopped korus WHPX probe (PID $($_.Id))" }
    else { Write-Host "Stopped orphan Korus QEMU (PID $($_.Id))" }
}
. (Join-Path $PSScriptRoot "lib\Stop-KorusRepoHttp.ps1")
Stop-KorusRepoHttp
Write-Host "[OK] QEMU VMs stopped" -ForegroundColor Green
