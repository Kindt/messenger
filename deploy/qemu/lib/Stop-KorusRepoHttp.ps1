function Stop-KorusRepoHttp {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $pidFile = Join-Path $KorusQemuRunDir "repo-http.pid"
    if (Test-Path $pidFile) {
        $procId = (Get-Content $pidFile -Raw).Trim()
        if ($procId -match '^\d+$') {
            Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped repo HTTP (PID $procId)" -ForegroundColor DarkGray
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}
