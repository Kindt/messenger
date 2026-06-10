function Stop-KorusRepoHttp {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $pidFile = Join-Path $KorusQemuRunDir "repo-http.pid"
    if (Test-Path $pidFile) {
        $old = (Get-Content $pidFile -Raw).Trim()
        if ($old -match '^\d+$') {
            Stop-Process -Id ([int]$old) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 300
}

function Start-KorusRepoHttp {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "New-KorusRepoSnapshot.ps1")

    $port = $KorusQemuRepoHttpPort
    $pidFile = Join-Path $KorusQemuRunDir "repo-http.pid"
    if (Test-Path $pidFile) {
        $old = (Get-Content $pidFile -Raw).Trim()
        if ($old -match '^\d+$' -and (Get-Process -Id ([int]$old) -ErrorAction SilentlyContinue)) {
            Write-Host "Repo HTTP already serving on 0.0.0.0:$port (PID $old)" -ForegroundColor DarkGray
            return [int]$old
        }
    }

    Stop-KorusRepoHttp
    New-KorusRepoSnapshot -StopRepoHttp | Out-Null

    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        throw "python not found (required for repo HTTP on host)"
    }

    Write-Host "Serving repo.tgz on 0.0.0.0:$port (guest: http://10.0.2.2:$port/repo.tgz) ..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath python -ArgumentList @(
        "-m", "http.server", "$port", "--bind", "0.0.0.0", "--directory", $KorusQemuRunDir
    ) -PassThru -WindowStyle Hidden
    $pidFile = Join-Path $KorusQemuRunDir "repo-http.pid"
    $proc.Id | Set-Content -Path $pidFile -Encoding ascii
    return $proc.Id
}
