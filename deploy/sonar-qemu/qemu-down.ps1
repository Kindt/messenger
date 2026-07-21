# Stop the SonarQube QEMU VM.
param([switch]$Help)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\qemu-down.ps1"
    exit 0
}

. (Join-Path $PSScriptRoot "config.ps1")
$pidFile = Join-Path $SonarQemuRunDir "sonar.pid"
$stopped = $false

if (Test-Path $pidFile) {
    $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($oldPid) {
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
        $stopped = $true
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$vmPattern = [regex]::Escape($SonarQemuVmName) + "|dqlclient-sonar"
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -eq "qemu-system-x86_64.exe" -and $_.CommandLine -match $vmPattern } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $stopped = $true
    }

if ($stopped) {
    Write-Host "[OK] Sonar QEMU VM stopped" -ForegroundColor Green
} else {
    Write-Host "No $SonarQemuVmName QEMU process found" -ForegroundColor DarkGray
}
