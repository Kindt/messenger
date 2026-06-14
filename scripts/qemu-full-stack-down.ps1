# Stop Korus QEMU VMs (full stack and dev share the same VM pair).
param([switch]$Help)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\qemu-full-stack-down.ps1"
    Write-Host "  Same as qemu-down.ps1; only stops korus-server / korus-web VMs."
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "scripts\qemu-down.ps1")
exit $LASTEXITCODE
