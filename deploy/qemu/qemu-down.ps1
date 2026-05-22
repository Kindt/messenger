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
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
. (Join-Path $PSScriptRoot "lib\Stop-KorusRepoHttp.ps1")
Stop-KorusRepoHttp
Write-Host "[OK] QEMU VMs stopped" -ForegroundColor Green
