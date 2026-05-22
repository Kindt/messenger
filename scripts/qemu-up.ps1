# Запуск двух QEMU ВМ (dev-server + web). См. deploy/qemu/README.md
param(
    [switch]$InstallQemuOnly,
    [switch]$SkipQemuInstall,
    [switch]$KeepDisks,
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-up.ps1 [-InstallQemuOnly] [-SkipQemuInstall] [-KeepDisks]"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "deploy\qemu\qemu-up.ps1") @PSBoundParameters
