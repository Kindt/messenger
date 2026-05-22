# Live QEMU build/stack dashboard (auto-refresh).
param(
    [int]$IntervalSeconds = 8,
    [int]$SshTimeoutSec = 18,
    [switch]$Once,
    [switch]$Help
)

$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "deploy\qemu\tools\qemu-watch.ps1") @PSBoundParameters
