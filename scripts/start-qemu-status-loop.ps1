# Start minute QEMU status loop without opening a visible PowerShell window.
param(
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$PidPath = Join-Path $RunDir "status-minute-loop.pid"
$LoopScript = Join-Path $PSScriptRoot "qemu-status-loop.ps1"

if ($Help) {
    Write-Host @"
Usage: .\scripts\start-qemu-status-loop.ps1 [-Force]

Starts scripts/qemu-status-loop.ps1 hidden (no extra window).
Writes log only — does NOT emit AGENT_LOOP_TICK to Cursor chat.
For chat reports use: .\scripts\qemu-chat-watch.ps1 (or qemu-status-loop in Cursor background terminal).

Log: deploy/qemu/run/status-minute.log
Stop: .\scripts\stop-qemu-status-loop.ps1
"@
    exit 0
}

if (-not (Test-Path $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }

if (Test-Path $PidPath) {
    $oldPid = [int](Get-Content $PidPath -Raw).Trim()
    if ((Get-Process -Id $oldPid -ErrorAction SilentlyContinue) -and -not $Force) {
        Write-Host "Status loop already running (PID $oldPid). Use -Force to restart." -ForegroundColor Yellow
        exit 0
    }
}

& (Join-Path $PSScriptRoot "stop-qemu-status-loop.ps1") -Quiet | Out-Null

$proc = Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -PassThru -ArgumentList @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $LoopScript
)
Set-Content -Path $PidPath -Value $proc.Id -Encoding ascii -NoNewline
Write-Host "QEMU status loop started hidden (PID $($proc.Id)). Log: deploy/qemu/run/status-minute.log" -ForegroundColor Green
