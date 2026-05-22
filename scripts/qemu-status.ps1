# Quick QEMU stack status
$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "deploy\qemu\tools\qemu-status.ps1") @args
