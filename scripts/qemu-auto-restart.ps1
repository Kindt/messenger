# Background QEMU restart — invoked by minute-status auto-remediate after pre-restart analysis.
param(
    [switch]$FreshDisks,
    [switch]$Help
)
$ErrorActionPreference = "Continue"
if ($Help) {
    Write-Host "Usage: .\scripts\qemu-auto-restart.ps1 [-FreshDisks]"
    Write-Host "  Default: qemu-up -KeepDisks. -FreshDisks resets VM disks (clean bootstrap)."
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Log = Join-Path $Root "deploy\qemu\run\status-remediate.log"
function Log([string]$Line) {
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [restart] $Line" | Add-Content -Path $Log -Encoding utf8
}
Log "begin qemu-down"
& (Join-Path $Root "scripts\qemu-down.ps1") 2>&1 | ForEach-Object { Log "  $_" }
Start-Sleep -Seconds 2
if ($FreshDisks) {
    Log "begin qemu-up (fresh disks, no -KeepDisks)"
    & (Join-Path $Root "scripts\qemu-up.ps1") 2>&1 | ForEach-Object { Log "  $_" }
} else {
    Log "begin qemu-up -KeepDisks"
    & (Join-Path $Root "scripts\qemu-up.ps1") -KeepDisks 2>&1 | ForEach-Object { Log "  $_" }
}
Log "done freshDisks=$([bool]$FreshDisks)"
$lock = Join-Path $Root "deploy\qemu\run\qemu-auto-restart.lock"
Remove-Item $lock -Force -ErrorAction SilentlyContinue
