# Запуск двух QEMU ВМ (dev-server + web). См. deploy/qemu/README.md
param(
    [switch]$InstallQemuOnly,
    [switch]$SkipQemuInstall,
    [switch]$KeepDisks,
    [switch]$Graphical,
    [ValidateSet("", "none", "gtk", "sdl", "default")]
    [string]$Display = "",
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-up.ps1 [-InstallQemuOnly] [-SkipQemuInstall] [-KeepDisks] [-Graphical] [-Display none|gtk|sdl|default]"
    Write-Host "  -Graphical   GTK windows for visual boot monitoring (same as -Display gtk)"
    Write-Host "  Env: KORUS_QEMU_DISPLAY=gtk|none|sdl|default"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "deploy\qemu\qemu-up.ps1") @PSBoundParameters
