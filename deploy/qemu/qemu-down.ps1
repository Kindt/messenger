. (Join-Path $PSScriptRoot "config.ps1")

foreach ($role in @("server", "web")) {
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
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -match "korus-server|korus-web|-machine none -display none -serial null") {
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        if ($cmd -match "korus-server") { Write-Host "Stopped orphan korus-server (PID $($_.Id))" }
        if ($cmd -match "korus-web") { Write-Host "Stopped orphan korus-web (PID $($_.Id))" }
    }
}
. (Join-Path $PSScriptRoot "lib\Stop-KorusRepoHttp.ps1")
Stop-KorusRepoHttp
Write-Host "[OK] QEMU VMs stopped" -ForegroundColor Green
