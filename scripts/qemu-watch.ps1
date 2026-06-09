# Live QEMU build/stack dashboard (auto-refresh + bootstrap log tail).
param(
    [int]$IntervalSeconds = 8,
    [int]$SshTimeoutSec = 18,
    [int]$BootstrapTailLines = 12,
    [switch]$Once,
    [switch]$CleanView,
    [switch]$ShowBootstrapRaw,
    [switch]$NewWindow,
    [switch]$Help
)

$Root = Split-Path -Parent $PSScriptRoot

if ($NewWindow) {
    if ($Help) {
        Write-Host "Opens qemu-watch in a new PowerShell window (same flags except -NewWindow)."
        exit 0
    }
    $watchPath = $PSCommandPath
    $argList = @(
        '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', $watchPath
    )
    if ($IntervalSeconds -ne 8) { $argList += '-IntervalSeconds'; $argList += $IntervalSeconds }
    if ($SshTimeoutSec -ne 18) { $argList += '-SshTimeoutSec'; $argList += $SshTimeoutSec }
    if ($BootstrapTailLines -ne 12) { $argList += '-BootstrapTailLines'; $argList += $BootstrapTailLines }
    if ($Once) { $argList += '-Once' }
    if ($CleanView) { $argList += '-CleanView' }
    if ($ShowBootstrapRaw) { $argList += '-ShowBootstrapRaw' }

    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $Root -ArgumentList $argList | Out-Null
    Write-Host "QEMU watch opened in a new window (refresh ${IntervalSeconds}s, bootstrap tail ${BootstrapTailLines} lines)." -ForegroundColor Green
    exit 0
}

& (Join-Path $Root "deploy\qemu\tools\qemu-watch.ps1") @PSBoundParameters
